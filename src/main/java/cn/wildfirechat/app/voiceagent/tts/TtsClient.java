package cn.wildfirechat.app.voiceagent.tts;

import cn.wildfirechat.app.voiceagent.audio.Pcm;
import cn.wildfirechat.app.voiceagent.audio.WavCodec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可插拔的 TTS 客户端。
 *
 * 野火自己有 ASR（wf-voice）但没有语音合成，所以这一层必须是可换的：默认按 OpenAI 的
 * /v1/audio/speech 格式发请求，本机 CosyVoice / GPT-SoVITS 的兼容封装、商业 TTS、
 * 客户内网自建服务，改配置即可切换，不动代码。这和 asr-api 里大模型那一层的做法一致。
 *
 * 请求体用模板拼，占位符 {text}/{model}/{voice}，这样非标准接口也能通过改配置适配：
 * 模型名和音色是最常改的两项（换发音人、换合成模型），单独提成配置项，
 * 不用为了换个音色去改一整行 JSON 模板。
 */
public class TtsClient {
    private static final Logger LOG = LoggerFactory.getLogger(TtsClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static class Audio {
        public final short[] mono;
        public final int sampleRate;

        Audio(short[] mono, int sampleRate) {
            this.mono = mono;
            this.sampleRate = sampleRate;
        }
    }

    private final OkHttpClient http;
    private final String url;
    private final String apiKey;
    private final String bodyTemplate;
    private final String model;
    private final String voice;
    private final String format;
    private final int rawSampleRate;

    /**
     * @param bodyTemplate  JSON 请求体模板，{text} 换成要合成的文本，{model}/{voice} 换成下面两项
     * @param model         合成模型名
     * @param voice         音色/发音人
     * @param format        wav = 响应是 wav 文件；pcm = 响应是裸 int16 小端 PCM
     * @param rawSampleRate format 为 pcm 时的采样率
     */
    public TtsClient(OkHttpClient http, String url, String apiKey, String bodyTemplate,
                     String model, String voice, String format, int rawSampleRate) {
        this.http = http;
        this.url = url;
        this.apiKey = apiKey;
        this.bodyTemplate = bodyTemplate;
        this.model = model == null ? "" : model;
        this.voice = voice == null ? "" : voice;
        this.format = format == null ? "wav" : format.trim().toLowerCase();
        this.rawSampleRate = rawSampleRate;
    }

    public Audio synthesize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String body = buildBody(text);
        boolean[] retryable = {false};
        Audio audio = request(body, text, retryable);
        // 合成失败在通话里就是凭空少一句话，网络抖动或服务端 5xx 值得再试一次
        if (audio == null && retryable[0]) {
            LOG.warn("TTS 失败，重试一次：{}", text);
            audio = request(body, text, new boolean[1]);
        }
        if (audio == null) {
            LOG.error("TTS 最终失败，这句话不会出声：{}", text);
        }
        return audio;
    }

    /** 模板拼请求体：先填 model/voice，最后填 text，免得文本里的花括号被再解释一遍 */
    private String buildBody(String text) {
        return bodyTemplate
                .replace("{model}", escapeJson(model))
                .replace("{voice}", escapeJson(voice))
                .replace("{text}", escapeJson(text));
    }

    /** @param retryable 出参：置为 true 表示这次失败是网络或 5xx，值得重试 */
    private Audio request(String body, String text, boolean[] retryable) {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON, body));
        if (apiKey != null && !apiKey.isEmpty()) {
            rb.header("Authorization", "Bearer " + apiKey);
        }

        long t0 = System.currentTimeMillis();
        try (Response resp = http.newCall(rb.build()).execute()) {
            ResponseBody rbody = resp.body();
            if (!resp.isSuccessful() || rbody == null) {
                retryable[0] = resp.code() >= 500;
                LOG.error("TTS 请求失败 {} {}：{}", text, resp.code(), rbody == null ? "" : rbody.string());
                return null;
            }
            byte[] data = rbody.bytes();
            Audio audio = decode(data);
            if (audio == null || audio.mono.length == 0) {
                retryable[0] = true;
                LOG.error("TTS 返回了空音频（{} 字节），文本：{}", data.length, text);
                return null;
            }
            LOG.debug("TTS 合成 {} 字，耗时 {}ms，音频 {}ms",
                    text.length(), System.currentTimeMillis() - t0,
                    audio.mono.length * 1000 / Math.max(1, audio.sampleRate));
            return audio;
        } catch (Exception e) {
            retryable[0] = true;
            LOG.error("TTS 请求出错：" + text, e);
            return null;
        }
    }

    private Audio decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        // 有些服务不管你要什么都回 wav，所以先按魔数认，认不出再按配置处理
        boolean looksWav = data.length > 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F';
        if (looksWav || "wav".equals(format)) {
            WavCodec.Wav wav = WavCodec.decode(data);
            return new Audio(wav.mono, wav.sampleRate);
        }
        short[] mono = Pcm.leToShorts(data, 0, data.length);
        return new Audio(mono, rawSampleRate);
    }

    static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
