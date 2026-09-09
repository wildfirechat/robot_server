package cn.wildfirechat.app.voiceagent;

import cn.wildfirechat.app.voiceagent.audio.PcmFormat;
import cn.wildfirechat.app.voiceagent.audio.Pcm;
import cn.wildfirechat.app.voiceagent.audio.PlaybackQueue;
import cn.wildfirechat.app.voiceagent.audio.Resampler;
import cn.wildfirechat.app.voiceagent.llm.LlmClient;
import cn.wildfirechat.app.voiceagent.tts.TtsClient;
import cn.wildfirechat.app.voiceagent.tts.WsTtsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 一通电话的对话编排：断句攒话 → LLM 流式生成 → 逐句合成 → 排进播放队列，
 * 以及被打断时把在途的这一切全部作废。
 *
 * 轮次（turn）是这里的核心。每一轮生成开始时 currentTurn 加一并被闭包捕获，
 * LLM 每读一行、TTS 每合成一句之前都对一次 turn；用户一开口，currentTurn 再加一，
 * 在途的 LLM 和 TTS 下一次检查时就会自己停下来，播放队列同时清空。
 * 不用中断线程，也不用共享取消标志，只靠一个单调递增的整数。
 */
public class VoiceAgentSession {
    private static final Logger LOG = LoggerFactory.getLogger(VoiceAgentSession.class);

    private final VoiceAgentConfig config;
    private final LlmClient llm;
    private final TtsClient tts;
    /** 流式 TTS（wf-tts WebSocket），为 null 时走 HTTP 整句合成 */
    private final WsTtsClient wsTts;
    private final PlaybackQueue playback;
    private final String callId;
    /** 通话文字记录的回调：(说话人, 文本)，说话人为 null 表示是 AI 说的 */
    private final BiConsumer<String, String> transcript;

    private final AtomicInteger currentTurn = new AtomicInteger();
    private final LinkedList<LlmClient.Message> history = new LinkedList<>();
    private final StringBuilder pending = new StringBuilder();
    private final Object pendingLock = new Object();

    /** LLM 一个线程，TTS 一个线程。TTS 单线程是为了保证句子的播放顺序 */
    private final ExecutorService llmExec;
    private final ExecutorService ttsExec;
    private final ScheduledExecutorService timer;
    private ScheduledFuture<?> pendingTask;

    private final java.util.concurrent.atomic.AtomicBoolean greeted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private volatile PcmFormat callFormat;
    private volatile Resampler ttsResampler;
    private volatile boolean closed;

    public VoiceAgentSession(VoiceAgentConfig config, LlmClient llm, TtsClient tts, WsTtsClient wsTts,
                             String callId, BiConsumer<String, String> transcript) {
        this.config = config;
        this.llm = llm;
        this.tts = tts;
        this.wsTts = wsTts;
        this.callId = callId;
        this.transcript = transcript;
        // 通话格式还不知道，先按 48k 立体声估一个上限，setCallFormat 里再按真实格式重算
        this.playback = new PlaybackQueue(Math.max(1, config.getPlaybackMaxSeconds()) * 48000 * 2 * 2);
        this.llmExec = Executors.newSingleThreadExecutor(r -> named(r, "va-llm-" + callId));
        this.ttsExec = Executors.newSingleThreadExecutor(r -> named(r, "va-tts-" + callId));
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> named(r, "va-timer-" + callId));
    }

    private static Thread named(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public PlaybackQueue getPlayback() {
        return playback;
    }

    /** 通话音频格式确定后调用，TTS 输出要重采样到这个格式才能塞回通话 */
    public void setCallFormat(PcmFormat format) {
        this.callFormat = format;
        // 队列上限得按真实格式换算成字节，否则 48k 立体声下能缓存的秒数只有 16k 单声道的六分之一
        playback.setCapacityBytes(bytesPerSecond(format) * Math.max(1, config.getPlaybackMaxSeconds()));
    }

    private static int bytesPerSecond(PcmFormat f) {
        return f.sampleRate * f.channels * f.bytesPerSample;
    }

    /** 接通后说开场白 */
    public void greet() {
        String greeting = config.getGreeting();
        if (greeting == null || greeting.trim().isEmpty()) {
            return;
        }
        // 通话状态回调可能重复触发，开场白只说一次
        if (!greeted.compareAndSet(false, true)) {
            return;
        }
        int turn = currentTurn.incrementAndGet();
        synchronized (history) {
            history.add(new LlmClient.Message("assistant", greeting));
        }
        speak(greeting, turn);
        transcript.accept(null, greeting);
    }

    /**
     * 收到一段 ASR 结果。
     *
     * VAD 经常把一句话切成好几段（"我想问一下" / "今天几号"），所以不能一段就生成一次。
     * 这里攒着，等 utteranceGapMs 内没有新段了才认为这句话真说完，再去生成。
     *
     * @param latencyMs 用户说完到出段的耗时（VAD 判停 + 识别），wf-voice 没带时间戳时为 null
     */
    public void onAsrSegment(String speaker, String text, Long latencyMs) {
        if (closed) {
            return;
        }
        if (latencyMs != null) {
            LOG.info("[{}] {} 说：{}（说完后 {}ms 出段）", callId, speaker, text, latencyMs);
        } else {
            LOG.info("[{}] {} 说：{}", callId, speaker, text);
        }
        transcript.accept(speaker, text);
        synchronized (pendingLock) {
            if (pending.length() > 0) {
                pending.append('，');
            }
            pending.append(text);
            if (pendingTask != null) {
                pendingTask.cancel(false);
            }
            pendingTask = timer.schedule(this::flushPending,
                    config.getUtteranceGapMs(), TimeUnit.MILLISECONDS);
        }
    }

    private void flushPending() {
        String utterance;
        synchronized (pendingLock) {
            if (pending.length() == 0) {
                return;
            }
            utterance = pending.toString();
            pending.setLength(0);
            pendingTask = null;
        }
        if (closed) {
            return;
        }
        int turn = currentTurn.incrementAndGet();
        // 新一轮开始，上一轮的残留音频（如果有）已经过时，清掉免得新回答排在旧回答后面
        playback.flush();
        LOG.info("[{}] 第 {} 轮开始，用户说完，触发回复：{}", callId, turn, utterance);
        synchronized (history) {
            history.add(new LlmClient.Message("user", utterance));
        }
        trimHistory();
        llmExec.submit(() -> generate(utterance, turn));
    }

    private void generate(String utterance, int turn) {
        long t0 = System.currentTimeMillis();
        List<LlmClient.Message> messages = new ArrayList<>();
        messages.add(new LlmClient.Message("system", config.getLlmSystemPrompt()));
        synchronized (history) {
            messages.addAll(history);
        }
        boolean[] firstSentence = {true};
        String full = llm.streamChat(messages, () -> alive(turn), sentence -> {
            if (firstSentence[0]) {
                firstSentence[0] = false;
                LOG.info("[{}] 第 {} 轮 LLM 首句就绪，用时 {}ms", callId, turn, System.currentTimeMillis() - t0);
            }
            speak(sentence, turn);
        });
        if (full != null && !full.trim().isEmpty() && alive(turn)) {
            LOG.info("[{}] 第 {} 轮 LLM 生成完成，共 {} 字，用时 {}ms",
                    callId, turn, full.trim().length(), System.currentTimeMillis() - t0);
            synchronized (history) {
                history.add(new LlmClient.Message("assistant", full.trim()));
            }
            transcript.accept(null, full.trim());
        } else if (full != null && full.trim().isEmpty() && alive(turn)) {
            // LLM 请求失败时不能让用户面对死寂，说一句兜底的
            LOG.warn("[{}] 第 {} 轮 LLM 没有返回内容，播报兜底语", callId, turn);
            speak("不好意思，我这边出了点问题，能麻烦再说一遍吗？", turn);
        } else if (!alive(turn)) {
            LOG.info("[{}] 第 {} 轮已被打断，已生成内容作废：{}", callId, turn,
                    full == null ? "" : full.trim());
        }
    }

    /** 一句话送去合成，合成完重采样到通话格式排进播放队列 */
    private void speak(String sentence, int turn) {
        ttsExec.submit(() -> {
            if (!alive(turn)) {
                return;
            }
            PcmFormat fmt = awaitCallFormat(turn);
            if (fmt == null) {
                LOG.warn("[{}] 通话音频格式一直没确定，丢弃这句合成结果：{}", callId, sentence);
                return;
            }
            if (wsTts != null && streamSpeak(sentence, turn, fmt)) {
                return;
            }
            httpSpeak(sentence, turn, fmt);
        });
    }

    /**
     * 流式合成：wf-tts 边合成边推 PCM，每收到一块就重采样进播放队列，
     * 不等整句合成完。返回 false 表示流式通道故障，调用方回退 HTTP。
     */
    private boolean streamSpeak(String sentence, int turn, PcmFormat fmt) {
        long t0 = System.currentTimeMillis();
        boolean[] firstChunk = {true};
        boolean[] starvedWarned = {false};
        Resampler[] rs = {null};
        boolean ok = wsTts.synthesize(sentence, (pcm, rate) -> {
            if (!alive(turn)) {
                return false;
            }
            Resampler r = rs[0];
            if (r == null || r.getSrcRate() != rate || r.getDstRate() != fmt.sampleRate) {
                r = new Resampler(rate, fmt.sampleRate);
                rs[0] = r;
            }
            short[] resampled = r.process(pcm);
            if (resampled.length == 0) {
                return true;
            }
            if (firstChunk[0]) {
                firstChunk[0] = false;
                LOG.info("[{}] 第 {} 轮首块音频就绪，用时 {}ms：{}", callId, turn,
                        System.currentTimeMillis() - t0, sentence);
            } else if (!starvedWarned[0] && playback.pendingBytes() == 0) {
                // 合成还在进行但队列已经空了：合成速度跟不上播放，用户正在听静音
                starvedWarned[0] = true;
                LOG.warn("[{}] 第 {} 轮播放队列断粮，合成跟不上播放（TTS 太慢）", callId, turn);
            }
            // 队列满就在这儿等播放腾空位，被 flush 叫醒说明本轮已作废
            return playback.offer(Pcm.fromMono(resampled, fmt.channels), () -> alive(turn));
        });
        if (ok && alive(turn)) {
            LOG.info("[{}] 第 {} 轮本句合成完成，用时 {}ms，播放队列积压 {}ms：{}", callId, turn,
                    System.currentTimeMillis() - t0, pendingMs(fmt), sentence);
        }
        if (!ok && alive(turn)) {
            LOG.warn("[{}] 流式合成通道故障，回退 HTTP 整句合成：{}", callId, sentence);
        }
        // 被打断（alive 变 false）也算这次合成已经处理过，不要回退 HTTP 再播一遍
        return ok || !alive(turn);
    }

    /** HTTP 整句合成：等整句音频返回后一次性入队，作为流式通道的兜底 */
    private void httpSpeak(String sentence, int turn, PcmFormat fmt) {
        if (!alive(turn)) {
            return;
        }
        TtsClient.Audio audio = tts.synthesize(sentence);
        if (audio == null || audio.mono.length == 0 || !alive(turn)) {
            return;
        }
        Resampler rs = ttsResampler;
        if (rs == null || rs.getSrcRate() != audio.sampleRate || rs.getDstRate() != fmt.sampleRate) {
            rs = new Resampler(audio.sampleRate, fmt.sampleRate);
            ttsResampler = rs;
        }
        short[] resampled = rs.process(audio.mono);
        if (!alive(turn)) {
            return;
        }
        // 队列满就在这儿等播放腾空位。TTS 比实时快，长回答必然堆积，
        // 这里等一等，比让队列丢掉正在播的句子强得多
        if (playback.offer(Pcm.fromMono(resampled, fmt.channels), () -> alive(turn))) {
            LOG.debug("[{}] 第 {} 轮入队 {}ms 音频，队列剩 {}ms：{}", callId, turn,
                    resampled.length * 1000L / Math.max(1, fmt.sampleRate),
                    pendingMs(fmt), sentence);
        } else {
            LOG.info("[{}] 第 {} 轮已作废，丢弃这句合成结果：{}", callId, turn, sentence);
        }
    }

    /**
     * 等通话格式确定。格式要等第一次 fetchRecordData 才知道，开场白经常赶在它前面，
     * 直接丢掉就是整句开场白没声。这里跑在 TTS 线程上，等一会儿不影响通话线程。
     */
    private PcmFormat awaitCallFormat(int turn) {
        for (int i = 0; i < 40 && callFormat == null && alive(turn); i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return callFormat;
    }

    private long pendingMs(PcmFormat fmt) {
        return playback.pendingBytes() * 1000L / Math.max(1, bytesPerSecond(fmt));
    }

    /**
     * 用户开口了：作废这一轮，把还没播的 AI 语音全丢掉。
     *
     * 即使播放队列是空的也要作废——用户可能是在 LLM 正在生成、TTS 正在合成时插的话，
     * 不作废的话 AI 会接着回答一个已经过时的问题。
     */
    public void bargeIn() {
        PcmFormat fmt = callFormat;
        long discardedMs = fmt == null ? -1 : pendingMs(fmt);
        int cancelled = currentTurn.get();
        currentTurn.incrementAndGet();
        playback.flush();
        // 打断和丢音在听感上是一回事，所以把丢掉多少毫秒打出来：
        // 排查时看这行就知道是真被打断了，还是回声把 SpeechGate 误触了
        if (discardedMs > 0) {
            LOG.info("[{}] 用户插话，作废第 {} 轮，丢弃未播音频 {}ms", callId, cancelled, discardedMs);
        } else {
            LOG.info("[{}] 用户插话，作废第 {} 轮在途的生成/合成", callId, cancelled);
        }
    }

    private boolean alive(int turn) {
        return !closed && turn == currentTurn.get();
    }

    private void trimHistory() {
        synchronized (history) {
            int max = Math.max(2, config.getLlmMaxHistoryTurns() * 2);
            while (history.size() > max) {
                history.removeFirst();
            }
        }
    }

    public void close() {
        closed = true;
        currentTurn.incrementAndGet();
        playback.close();
        if (wsTts != null) {
            wsTts.close();
        }
        timer.shutdownNow();
        llmExec.shutdownNow();
        ttsExec.shutdownNow();
    }
}
