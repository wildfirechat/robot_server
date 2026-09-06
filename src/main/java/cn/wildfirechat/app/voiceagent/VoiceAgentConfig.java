package cn.wildfirechat.app.voiceagent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** 语音 Agent 的配置，见 config/voiceagent.properties。 */
@Configuration
@ConfigurationProperties(prefix = "agent")
@PropertySource(value = "file:config/voiceagent.properties", encoding = "UTF-8", ignoreResourceNotFound = true)
public class VoiceAgentConfig {
    /** 关掉就退回原来的 EchoAudioDevice（延迟 3 秒复读），方便对比排查 */
    private boolean enabled = true;

    // ---- ASR：wf-voice 实时流 ----
    private String asrUrl = "ws://127.0.0.1:12436";
    /** 攒够多少毫秒音频发一帧给 ASR。太碎则小包多，太久则拖慢断句 */
    private int asrBatchMs = 60;

    // ---- LLM：任何 OpenAI 格式的服务 ----
    private String llmUrl = "http://127.0.0.1:11434/v1/chat/completions";
    private String llmKey = "";
    private String llmModel = "qwen2.5:7b-instruct";
    private double llmTemperature = 0.6;
    private String llmSystemPrompt =
            "你是一个电话语音助手。你的回答会被合成成语音念给对方听，所以必须口语化、简短，"
            + "一次只说一两句话，不要用 Markdown、不要用列表、不要用括号注释、不要念表情符号。"
            + "如果对方的问题不清楚，就直接反问一句。";
    /** 保留多少轮对话作为上下文（一轮 = 一问一答） */
    private int llmMaxHistoryTurns = 12;
    /** 软标点断句的最小字数，太短的半句不值得单独送去合成 */
    private int llmMinSentenceChars = 8;
    /** 迟迟没有标点时的强制断句字数，防止一直不出声 */
    private int llmMaxSentenceChars = 60;

    // ---- TTS：野火没有自带合成，必须外挂 ----
    private String ttsUrl = "http://127.0.0.1:9880/v1/audio/speech";
    private String ttsKey = "";
    /** 合成用的模型名，填进模板的 {model} */
    private String ttsModel = "tts-1";
    /** 音色/发音人，填进模板的 {voice}。不同 TTS 叫法不一样：speaker、spk_id、reference_id 等 */
    private String ttsVoice = "keqing";
    /** 请求体模板，{text} 换成要合成的文本，{model}/{voice} 换成上面两项 */
    private String ttsBodyTemplate =
            "{\"model\":\"{model}\",\"voice\":\"{voice}\",\"response_format\":\"wav\",\"speed\":1.0,\"input\":\"{text}\"}";
    /** wav = 响应是 wav 文件；pcm = 响应是裸 int16 小端 PCM */
    private String ttsFormat = "wav";
    /** ttsFormat=pcm 时的采样率 */
    private int ttsRawSampleRate = 24000;

    // ---- 打断 ----
    private boolean bargeInEnabled = true;
    /** 帧能量 RMS 阈值（0~32768）。客户端有回声消除，收到的基本是纯净人声，给低一点没关系 */
    private double bargeInRmsThreshold = 900;
    /** 连续多少帧（每帧 10ms）超过阈值才算用户真的开口，防止一声咳嗽就打断 */
    private int bargeInFrames = 6;

    // ---- 对话节奏 ----
    /** ASR 出一段之后再等这么久，没有新段才认为这句话说完了。VAD 常把一句话切成几段 */
    private int utteranceGapMs = 400;
    /** 来电后多久接听。原来的复读 demo 是 3 秒，语音助手应该快一点 */
    private int answerDelayMs = 800;
    /** 接通后的开场白，留空则不主动说话 */
    private String greeting = "你好，我是野火语音助手，有什么可以帮你的？";
    /**
     * 播放队列最多缓存多少秒 AI 语音。上限必须按秒算再乘通话采样率和声道数：
     * 写死字节数的话，48k 立体声下 640000 字节只有 3.3 秒，一段稍长的回答就会
     * 把还没播完的句子挤掉，听起来就是无缘无故少了几个字。
     */
    private int playbackMaxSeconds = 60;

    // ---- 留痕 ----
    /** 把通话的文字记录同步发到 IM 会话里，通话结束后可回看，也是天然的审计留痕 */
    private boolean transcriptToIm = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public String getAsrUrl() { return asrUrl; }
    public void setAsrUrl(String v) { this.asrUrl = v; }
    public int getAsrBatchMs() { return asrBatchMs; }
    public void setAsrBatchMs(int v) { this.asrBatchMs = v; }
    public String getLlmUrl() { return llmUrl; }
    public void setLlmUrl(String v) { this.llmUrl = v; }
    public String getLlmKey() { return llmKey; }
    public void setLlmKey(String v) { this.llmKey = v; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String v) { this.llmModel = v; }
    public double getLlmTemperature() { return llmTemperature; }
    public void setLlmTemperature(double v) { this.llmTemperature = v; }
    public String getLlmSystemPrompt() { return llmSystemPrompt; }
    public void setLlmSystemPrompt(String v) { this.llmSystemPrompt = v; }
    public int getLlmMaxHistoryTurns() { return llmMaxHistoryTurns; }
    public void setLlmMaxHistoryTurns(int v) { this.llmMaxHistoryTurns = v; }
    public int getLlmMinSentenceChars() { return llmMinSentenceChars; }
    public void setLlmMinSentenceChars(int v) { this.llmMinSentenceChars = v; }
    public int getLlmMaxSentenceChars() { return llmMaxSentenceChars; }
    public void setLlmMaxSentenceChars(int v) { this.llmMaxSentenceChars = v; }
    public String getTtsUrl() { return ttsUrl; }
    public void setTtsUrl(String v) { this.ttsUrl = v; }
    public String getTtsKey() { return ttsKey; }
    public void setTtsKey(String v) { this.ttsKey = v; }
    public String getTtsModel() { return ttsModel; }
    public void setTtsModel(String v) { this.ttsModel = v; }
    public String getTtsVoice() { return ttsVoice; }
    public void setTtsVoice(String v) { this.ttsVoice = v; }
    public String getTtsBodyTemplate() { return ttsBodyTemplate; }
    public void setTtsBodyTemplate(String v) { this.ttsBodyTemplate = v; }
    public String getTtsFormat() { return ttsFormat; }
    public void setTtsFormat(String v) { this.ttsFormat = v; }
    public int getTtsRawSampleRate() { return ttsRawSampleRate; }
    public void setTtsRawSampleRate(int v) { this.ttsRawSampleRate = v; }
    public boolean isBargeInEnabled() { return bargeInEnabled; }
    public void setBargeInEnabled(boolean v) { this.bargeInEnabled = v; }
    public double getBargeInRmsThreshold() { return bargeInRmsThreshold; }
    public void setBargeInRmsThreshold(double v) { this.bargeInRmsThreshold = v; }
    public int getBargeInFrames() { return bargeInFrames; }
    public void setBargeInFrames(int v) { this.bargeInFrames = v; }
    public int getUtteranceGapMs() { return utteranceGapMs; }
    public void setUtteranceGapMs(int v) { this.utteranceGapMs = v; }
    public int getAnswerDelayMs() { return answerDelayMs; }
    public void setAnswerDelayMs(int v) { this.answerDelayMs = v; }
    public String getGreeting() { return greeting; }
    public void setGreeting(String v) { this.greeting = v; }
    public int getPlaybackMaxSeconds() { return playbackMaxSeconds; }
    public void setPlaybackMaxSeconds(int v) { this.playbackMaxSeconds = v; }
    public boolean isTranscriptToIm() { return transcriptToIm; }
    public void setTranscriptToIm(boolean v) { this.transcriptToIm = v; }
}
