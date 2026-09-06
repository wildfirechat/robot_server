package cn.wildfirechat.app.voiceagent.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * OpenAI 格式的流式对话客户端，兼容 vLLM / Ollama / 通义 / DeepSeek 等任何
 * 提供 /v1/chat/completions 的服务，换地址即可，不动代码。
 *
 * 关键在于**边生成边断句**：模型吐字的同时，一旦攒出一个完整小句就立刻交给 TTS，
 * 不等整段生成完。这是把首字延迟压下来的主要手段——否则光等模型说完就要好几秒。
 */
public class LlmClient {
    private static final Logger LOG = LoggerFactory.getLogger(LlmClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 断句用的收尾标点。逗号也算，因为口语里逗号处本来就有停顿。 */
    private static final String BREAKERS = "。！？!?…；;：:\n";
    private static final String SOFT_BREAKERS = "，,、";

    private final OkHttpClient http;
    private final String url;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int minSentenceChars;
    private final int maxSentenceChars;

    public LlmClient(OkHttpClient http, String url, String apiKey, String model,
                     double temperature, int minSentenceChars, int maxSentenceChars) {
        this.http = http;
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.minSentenceChars = minSentenceChars;
        this.maxSentenceChars = maxSentenceChars;
    }

    public static class Message {
        public final String role;
        public final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * 流式生成，按句回调。
     *
     * @param alive       每次回调前检查，返回 false 表示这一轮已被打断，立刻停止
     * @param onSentence  攒够一个小句就回调一次
     * @return 本轮完整的回复文本；被打断时返回已经生成的部分
     */
    public String streamChat(List<Message> messages, BooleanSupplier alive, Consumer<String> onSentence) {
        JsonArray arr = new JsonArray();
        for (Message m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            o.addProperty("content", m.content);
            arr.add(o);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", true);
        body.addProperty("temperature", temperature);
        body.add("messages", arr);

        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON, body.toString()));
        if (apiKey != null && !apiKey.isEmpty()) {
            rb.header("Authorization", "Bearer " + apiKey);
        }

        StringBuilder full = new StringBuilder();
        StringBuilder buf = new StringBuilder();

        try (Response resp = http.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                LOG.error("LLM 请求失败 {}：{}", resp.code(), safeBody(resp));
                return "";
            }
            ResponseBody rbody = resp.body();
            if (rbody == null) {
                return "";
            }
            BufferedReader reader = new BufferedReader(rbody.charStream());
            String line;
            while ((line = reader.readLine()) != null) {
                if (!alive.getAsBoolean()) {
                    LOG.info("本轮已被打断，停止读取 LLM 流");
                    break;
                }
                if (line.isEmpty() || !line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                String delta = extractDelta(data);
                if (delta == null || delta.isEmpty()) {
                    continue;
                }
                full.append(delta);
                buf.append(delta);
                int cut = findCut(buf);
                if (cut > 0) {
                    String sentence = buf.substring(0, cut).trim();
                    buf.delete(0, cut);
                    if (!sentence.isEmpty() && alive.getAsBoolean()) {
                        onSentence.accept(sentence);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("LLM 流式请求出错", e);
        }

        String tail = buf.toString().trim();
        if (!tail.isEmpty() && alive.getAsBoolean()) {
            onSentence.accept(tail);
        }
        return full.toString();
    }

    /**
     * 找断句点。硬标点（句号问号换行）立刻断；软标点（逗号顿号）要攒够 minSentenceChars
     * 才断，避免把"你好，"这种半句单独送去合成；超过 maxSentenceChars 则强行断，
     * 防止模型一口气不带标点写一长段导致迟迟出不了声。
     */
    private int findCut(StringBuilder buf) {
        for (int i = 0; i < buf.length(); i++) {
            char c = buf.charAt(i);
            if (BREAKERS.indexOf(c) >= 0) {
                return i + 1;
            }
            if (SOFT_BREAKERS.indexOf(c) >= 0 && i + 1 >= minSentenceChars) {
                return i + 1;
            }
        }
        return buf.length() >= maxSentenceChars ? buf.length() : -1;
    }

    private static String extractDelta(String data) {
        try {
            JsonObject o = JsonParser.parseString(data).getAsJsonObject();
            JsonArray choices = o.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return null;
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject delta = choice.getAsJsonObject("delta");
            if (delta == null || !delta.has("content") || delta.get("content").isJsonNull()) {
                return null;
            }
            return delta.get("content").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeBody(Response resp) {
        try {
            ResponseBody b = resp.body();
            return b == null ? "" : b.string();
        } catch (Exception e) {
            return "";
        }
    }
}
