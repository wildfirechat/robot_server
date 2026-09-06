package cn.wildfirechat.app.voiceagent;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.voiceagent.llm.LlmClient;
import cn.wildfirechat.app.voiceagent.tts.TtsClient;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.sdk.RobotService;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 语音 Agent 的装配点：共享的 HTTP 客户端、LLM、TTS 在这里建一次，
 * 每来一通电话生成一个 session 和一个 AiAudioDevice。
 */
@Component
public class VoiceAgentFactory {
    private static final Logger LOG = LoggerFactory.getLogger(VoiceAgentFactory.class);

    @Autowired
    private VoiceAgentConfig config;

    @Autowired
    private RobotConfig robotConfig;

    private OkHttpClient http;
    private LlmClient llm;
    private TtsClient tts;
    private RobotService robotService;

    private final java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong();

    @PostConstruct
    public void init() {
        // 读超时要留够：LLM 流式响应两个 token 之间可能隔好几秒
        http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        llm = new LlmClient(http, config.getLlmUrl(), config.getLlmKey(), config.getLlmModel(),
                config.getLlmTemperature(), config.getLlmMinSentenceChars(), config.getLlmMaxSentenceChars());
        tts = new TtsClient(http, config.getTtsUrl(), config.getTtsKey(), config.getTtsBodyTemplate(),
                config.getTtsModel(), config.getTtsVoice(),
                config.getTtsFormat(), config.getTtsRawSampleRate());
        robotService = new RobotService(robotConfig.getIm_url(), robotConfig.getIm_id(), robotConfig.getIm_secret());
        LOG.info("语音 Agent 已就绪：enabled={} asr={} llm={}({}) tts={}({}/{})",
                config.isEnabled(), config.getAsrUrl(), config.getLlmUrl(), config.getLlmModel(),
                config.getTtsUrl(), config.getTtsModel(), config.getTtsVoice());
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public int getAnswerDelayMs() {
        return config.isEnabled() ? config.getAnswerDelayMs() : 3000;
    }

    /**
     * 为一通电话创建音频设备。
     *
     * 呼出时 avEngineKit.startPrivateCall 要求先传入 AudioDevice 才会返回 CallSession，
     * 所以这里不能按 callId 建索引，用一个自增序号做日志标识，生命周期由调用方
     * 拿着 device 引用管理（接通调 getSession().greet()，挂断调 close()）。
     */
    public AiAudioDevice createDevice(Conversation conversation) {
        String tag = "call-" + seq.incrementAndGet();
        VoiceAgentSession session = new VoiceAgentSession(config, llm, tts, tag,
                (speaker, text) -> sendTranscript(conversation, speaker, text));
        return new AiAudioDevice(config, session, http, tag);
    }

    /**
     * 把通话内容以文字消息发回会话。
     * 对用户是"通话记录可回看"，对政企客户这就是天然的审计留痕——
     * 谁在什么时候跟 AI 说了什么、AI 答了什么，全都落在 IM 的消息库里。
     */
    private void sendTranscript(Conversation conversation, String speaker, String text) {
        if (!config.isTranscriptToIm() || conversation == null || text == null || text.trim().isEmpty()) {
            return;
        }
        try {
            MessagePayload payload = new MessagePayload();
            payload.setType(1);
            String prefix = speaker == null ? "🤖 " : "🎤 ";
            payload.setSearchableContent(prefix + text.trim());
            // PersistFlag.Persist(1)：本地存下来可回看，但不计未读数，
            // 免得通话过程中手机的未读角标一直跳。用 0(No_Persist) 就存不下来了，
            // 那样通话记录和审计留痕都没了。
            payload.setPersistFlag(1);
            robotService.sendMessage(robotConfig.getIm_id(), conversation, payload);
        } catch (Exception e) {
            LOG.warn("发送通话文字记录失败：{}", e.toString());
        }
    }
}
