package cn.wildfirechat.app.voiceagent;

import cn.wildfirechat.AudioDevice;
import cn.wildfirechat.CallSession;
import cn.wildfirechat.app.voiceagent.asr.AsrStreamClient;
import cn.wildfirechat.app.voiceagent.audio.Pcm;
import cn.wildfirechat.app.voiceagent.audio.PcmFormat;
import cn.wildfirechat.app.voiceagent.audio.Resampler;
import cn.wildfirechat.app.voiceagent.audio.SpeechGate;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 把通话音频接到 AI 上：AudioDevice 是野火服务端音视频 SDK 给出的裸 PCM 读写口，
 * 收到的音频带 userId（按说话人分开），这一点省掉了会议 AI 里最脏的说话人分离。
 *
 * 收：playoutData → 下混单声道 → 能量判打断 → 重采样到 16k → 送 wf-voice 实时识别
 * 发：fetchRecordData → 从播放队列取一帧 AI 语音，没有就补静音
 *
 * 这两个回调跑在 WebRTC 的音频线程上，每 10ms 一次，所以里面绝对不能有阻塞操作：
 * 识别、生成、合成全部在别的线程上做，这里只做定长的内存搬运。
 */
public class AiAudioDevice implements AudioDevice {
    private static final Logger LOG = LoggerFactory.getLogger(AiAudioDevice.class);
    private static final int ASR_RATE = 16000;

    private final VoiceAgentConfig config;
    private final VoiceAgentSession session;
    private final OkHttpClient http;
    private final String callId;

    private final Map<String, Speaker> speakers = new ConcurrentHashMap<>();
    private final SpeechGate gate;
    private final ScheduledExecutorService pinger;

    private volatile PcmFormat format;
    private volatile boolean closed;
    private long droppedFrames;

    public AiAudioDevice(VoiceAgentConfig config, VoiceAgentSession session,
                         OkHttpClient http, String callId) {
        this.config = config;
        this.session = session;
        this.http = http;
        this.callId = callId;
        this.gate = new SpeechGate(config.getBargeInRmsThreshold(), config.getBargeInFrames());
        this.pinger = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "va-ping-" + callId);
            t.setDaemon(true);
            return t;
        });
        this.pinger.scheduleAtFixedRate(this::pingAll, 30, 30, TimeUnit.SECONDS);
    }

    private class Speaker {
        final Resampler resampler;
        final AsrStreamClient asr;

        Speaker(String userId) {
            this.resampler = new Resampler(format.sampleRate, ASR_RATE);
            this.asr = new AsrStreamClient(http, config.getAsrUrl(),
                    callId + "_" + userId, config.getAsrBatchMs(),
                    text -> session.onAsrSegment(userId, text));
            this.asr.connect();
        }
    }

    private void pingAll() {
        for (Speaker s : speakers.values()) {
            try {
                s.asr.ping();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public int initPlayout(CallSession callSession, String userId) {
        return 0;
    }

    @Override
    public int stopPlayout(CallSession callSession, String userId) {
        Speaker s = speakers.remove(userId);
        if (s != null) {
            s.asr.close();
        }
        return 0;
    }

    @Override
    public int initRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int startRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int stopRecording(CallSession callSession) {
        return 0;
    }

    /** 通话向我们要一帧音频：从播放队列取，取不到就补静音 */
    @Override
    public void fetchRecordData(CallSession callSession, byte[] sampleData, int nSamples,
                                int nSampleBytes, int nChannels, int nSampleRate, int nBuffSize) {
        PcmFormat f = format;
        if (f == null || f.sampleRate != nSampleRate || f.channels != nChannels
                || f.bytesPerSample != nSampleBytes) {
            f = new PcmFormat(nSampleRate, nChannels, nSampleBytes);
            format = f;
            session.setCallFormat(f);
            LOG.info("[{}] 通话音频格式：{}，每帧 {} 字节", callId, f, nBuffSize);
        }
        session.getPlayback().read(sampleData, nBuffSize);
    }

    /** 通话把对端音频给我们：判打断，然后送去识别 */
    @Override
    public void playoutData(CallSession callSession, String userId, byte[] sampleData, int nBuffSize) {
        if (closed) {
            return;
        }
        PcmFormat f = format;
        if (f == null) {
            // fetchRecordData 还没被调用过，格式未知。通话刚接通的头几十毫秒而已，
            // 这时候人还没开口，丢掉没有影响。
            if (++droppedFrames % 100 == 1) {
                LOG.debug("[{}] 音频格式未知，已丢弃 {} 帧", callId, droppedFrames);
            }
            return;
        }
        try {
            short[] mono = Pcm.toMono(sampleData, nBuffSize, f.channels);
            if (config.isBargeInEnabled() && gate.feed(mono)) {
                session.bargeIn();
            }
            Speaker sp = speakers.get(userId);
            if (sp == null) {
                sp = speakers.computeIfAbsent(userId, Speaker::new);
            }
            sp.asr.sendPcm(sp.resampler.process(mono));
        } catch (Exception e) {
            LOG.error("[" + callId + "] 处理对端音频出错", e);
        }
    }

    public VoiceAgentSession getSession() {
        return session;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        session.close();
        pinger.shutdownNow();
        for (Speaker s : speakers.values()) {
            s.asr.close();
        }
        speakers.clear();
    }
}
