package cn.wildfirechat.app.voiceagent.asr;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * wf-voice 实时识别的 WebSocket 客户端（默认 ws://host:12436）。
 *
 * 协议见 wf-voice/docs/server-api.md：
 *   1. 连上之后第一条 TEXT 帧是 client_id；
 *   2. 之后持续发 16kHz/单声道/int16 小端的裸 PCM（BINARY 帧）；
 *   3. 服务端内置 Silero VAD，判定一段话说完之后回一条 TEXT：
 *      [<段开始的 unix 毫秒>+<时长秒>] <识别文本>
 *   4. 心跳发 TEXT "ping"，回 "pong"。
 *
 * 一个说话人一条连接：wf-voice 的 VAD 状态是按连接维护的，混在一起会互相干扰。
 * 而 AudioDevice.playoutData 正好带 userId，天然能分开。
 */
public class AsrStreamClient {
    private static final Logger LOG = LoggerFactory.getLogger(AsrStreamClient.class);
    private static final Pattern SEGMENT = Pattern.compile("^\\[(\\d+)\\+([\\d.]+)]\\s*(.*)$", Pattern.DOTALL);

    private final String url;
    private final String clientId;
    private final OkHttpClient http;
    private final Consumer<String> onText;
    private final int batchBytes;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private volatile WebSocket ws;
    private volatile boolean registered;
    /** 断线重连。重连期间送进来的音频会被丢掉（VAD 状态也丢了），但通话不哑 */
    private final ScheduledExecutorService retryTimer;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();

    /**
     * @param batchMs 攒够多少毫秒的音频再发一帧。发太碎会有大量小包开销，
     *                攒太久会拖慢 VAD 的断句判定，默认 60ms（约 2 个 VAD chunk）。
     */
    public AsrStreamClient(OkHttpClient http, String url, String clientId, int batchMs, Consumer<String> onText) {
        this.http = http;
        this.url = url;
        this.clientId = clientId;
        this.onText = onText;
        this.batchBytes = Math.max(1, 16000 / 1000 * batchMs) * 2;
        this.retryTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "asr-retry-" + clientId);
            t.setDaemon(true);
            return t;
        });
    }

    public void connect() {
        if (closed.get()) {
            return;
        }
        Request request = new Request.Builder().url(url).build();
        http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                ws = webSocket;
                registered = true;
                LOG.info("ASR 已连接 {} client_id={}", url, clientId);
                webSocket.send(clientId);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (webSocket == ws) {
                    handleText(text);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (webSocket != ws) {
                    return;
                }
                registered = false;
                if (!closed.get()) {
                    LOG.warn("ASR 连接断开 client_id={}：{}，稍后重连", clientId, t.toString());
                    scheduleReconnect();
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (webSocket != ws) {
                    return;
                }
                registered = false;
                if (!closed.get()) {
                    LOG.warn("ASR 连接被关闭 client_id={} code={}，稍后重连", clientId, code);
                    scheduleReconnect();
                }
            }
        });
    }

    private void scheduleReconnect() {
        if (closed.get() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        retryTimer.schedule(() -> {
            reconnectScheduled.set(false);
            if (!closed.get()) {
                connect();
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void handleText(String text) {
        if (text == null) {
            return;
        }
        String t = text.trim();
        if (t.isEmpty() || "pong".equalsIgnoreCase(t)) {
            return;
        }
        if (t.startsWith("[TRIAL]")) {
            LOG.warn("wf-voice 是体验版，单条流最多 30 秒：{}", t);
            return;
        }
        Matcher m = SEGMENT.matcher(t);
        String content = m.matches() ? m.group(3).trim() : t;
        // wf-voice 会把句号换成逗号，末尾的逗号对语义没用，去掉
        content = content.replaceAll("[，,。\\s]+$", "").trim();
        if (content.isEmpty()) {
            return;
        }
        try {
            onText.accept(content);
        } catch (Exception e) {
            LOG.error("处理识别结果出错", e);
        }
    }

    /** 送入 16kHz 单声道采样，内部攒够一批再发。 */
    public void sendPcm(short[] mono16k) {
        if (closed.get() || mono16k.length == 0) {
            return;
        }
        WebSocket s = ws;
        if (s == null || !registered) {
            return;
        }
        byte[] out;
        synchronized (pending) {
            for (short v : mono16k) {
                pending.write(v & 0xff);
                pending.write((v >> 8) & 0xff);
            }
            if (pending.size() < batchBytes) {
                return;
            }
            out = pending.toByteArray();
            pending.reset();
        }
        s.send(ByteString.of(out));
    }

    public void ping() {
        WebSocket s = ws;
        if (s != null && registered) {
            s.send("ping");
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            retryTimer.shutdownNow();
            WebSocket s = ws;
            if (s != null) {
                s.close(1000, "call ended");
            }
        }
    }
}
