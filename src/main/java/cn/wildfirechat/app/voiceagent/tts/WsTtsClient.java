package cn.wildfirechat.app.voiceagent.tts;

import cn.wildfirechat.app.voiceagent.audio.Pcm;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * wf-tts 的 WebSocket 流式合成客户端（默认 ws://host:12438）。
 *
 * 协议（见 wf-tts/docs/server-api.md）：发一条 JSON 文本帧
 * {"text":"...","voice":"..."}，服务端先回 {"event":"start","sample_rate":24000}，
 * 随后持续推 BINARY 帧（16bit 小端单声道 PCM），结束回 {"event":"end"}，
 * 失败回 {"event":"error","message":"..."}。客户端断开，服务端会尽快停止当前合成。
 *
 * 相比 HTTP 整句合成，这里边收边回调，首声延迟从"整句合成完"压到"首包延迟"。
 * 注意一条连接同一时刻只能跑一个合成（多 slot 并发时帧会交错），所以 synthesize
 * 是阻塞式的，由调用方（TTS 单线程）保证句子按顺序合成。
 */
public class WsTtsClient {
    private static final Logger LOG = LoggerFactory.getLogger(WsTtsClient.class);

    /** 音频块回调。返回 false 表示本轮已作废（被打断），立刻中断这次合成。 */
    public interface ChunkListener {
        boolean onChunk(short[] pcm, int sampleRate);
    }

    private static final class Start {
        final int sampleRate;
        Start(int sampleRate) { this.sampleRate = sampleRate; }
    }

    private static final class Chunk {
        final byte[] data;
        Chunk(byte[] data) { this.data = data; }
    }

    private static final class End {
        final double duration;
        final double cost;
        End(double duration, double cost) { this.duration = duration; this.cost = cost; }
    }

    private static final class Fail {
        final String message;
        Fail(String message) { this.message = message; }
    }

    private final OkHttpClient http;
    private final String url;
    private final String voice;

    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile WebSocket ws;
    private volatile boolean opened;

    public WsTtsClient(OkHttpClient http, String url, String voice) {
        this.http = http;
        this.url = url;
        this.voice = voice == null ? "" : voice;
    }

    /**
     * 阻塞式合成：发出文本后逐块回调，直到合成结束。
     *
     * @return true 表示这次合成走完了（包括被 listener 中止）；false 表示流式通道
     *         本身失败（连接不上、服务端报错、超时），调用方可以回退 HTTP 合成
     */
    public synchronized boolean synthesize(String text, ChunkListener listener) {
        if (closed.get() || text == null || text.trim().isEmpty()) {
            return true;
        }
        if (!ensureConnected()) {
            return false;
        }
        events.clear();
        String body = "{\"text\":\"" + TtsClient.escapeJson(text.trim())
                + "\",\"voice\":\"" + TtsClient.escapeJson(voice) + "\"}";
        WebSocket s = ws;
        if (s == null || !s.send(body)) {
            markDead();
            return false;
        }

        int sampleRate = 24000;
        try {
            while (true) {
                Object ev = events.poll(60, TimeUnit.SECONDS);
                if (ev == null) {
                    LOG.error("TTS 流式合成 60 秒没有新数据，断开连接：{}", text);
                    markDead();
                    return false;
                }
                if (ev instanceof Start) {
                    sampleRate = ((Start) ev).sampleRate;
                } else if (ev instanceof Chunk) {
                    byte[] data = ((Chunk) ev).data;
                    short[] pcm = Pcm.leToShorts(data, 0, data.length);
                    if (pcm.length > 0 && !listener.onChunk(pcm, sampleRate)) {
                        // 本轮已作废：断开连接让服务端尽快停止，下次合成时重连
                        markDead();
                        return true;
                    }
                } else if (ev instanceof End) {
                    End end = (End) ev;
                    LOG.debug("TTS 流式合成完成，音频 {}s，耗时 {}s：{}", end.duration, end.cost, text);
                    return true;
                } else if (ev instanceof Fail) {
                    LOG.error("TTS 流式合成失败（{}）：{}", ((Fail) ev).message, text);
                    return false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true; // 通话关闭导致的线程中断，不算通道故障
        }
    }

    private boolean ensureConnected() {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (opened && ws != null) {
                return true;
            }
            CountDownLatch latch = new CountDownLatch(1);
            Request request = new Request.Builder().url(url).build();
            http.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    ws = webSocket;
                    opened = true;
                    LOG.info("TTS 流式通道已连接 {}", url);
                    latch.countDown();
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    if (webSocket != ws) {
                        return;
                    }
                    handleText(text);
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    if (webSocket != ws) {
                        return;
                    }
                    events.offer(new Chunk(bytes.toByteArray()));
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    if (webSocket == ws) {
                        opened = false;
                        events.offer(new Fail(t == null ? "unknown" : t.toString()));
                    }
                    latch.countDown();
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    if (webSocket == ws) {
                        opened = false;
                        events.offer(new Fail("连接关闭 " + code + " " + reason));
                    }
                }
            });
            try {
                if (latch.await(5, TimeUnit.SECONDS) && opened) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            LOG.warn("TTS 流式通道连接失败，重试一次：{}", url);
        }
        return false;
    }

    private void handleText(String text) {
        String event;
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            event = o.has("event") ? o.get("event").getAsString() : "";
            switch (event) {
                case "start":
                    events.offer(new Start(o.has("sample_rate") ? o.get("sample_rate").getAsInt() : 24000));
                    break;
                case "end":
                    events.offer(new End(o.has("duration") ? o.get("duration").getAsDouble() : 0,
                            o.has("cost") ? o.get("cost").getAsDouble() : 0));
                    break;
                case "error":
                    events.offer(new Fail(o.has("message") ? o.get("message").getAsString() : text));
                    break;
                case "trial":
                    LOG.warn("wf-tts 是体验版：{}", text);
                    break;
                default:
                    LOG.warn("wf-tts 返回了未识别的消息：{}", text);
            }
        } catch (Exception e) {
            LOG.warn("解析 wf-tts 消息失败：{}", text);
        }
    }

    /** 当前连接作废：关掉并置空，下次合成时重连 */
    private void markDead() {
        opened = false;
        WebSocket s = ws;
        ws = null;
        if (s != null) {
            s.close(1000, "abort");
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            markDead();
        }
    }
}
