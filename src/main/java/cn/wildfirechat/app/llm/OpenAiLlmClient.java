package cn.wildfirechat.app.llm;

import cn.wildfirechat.app.RobotConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAI 兼容的大模型客户端，支持 /chat/completions 的流式（SSE）与非流式调用。
 * 兼容 OpenAI、DeepSeek、通义千问、vLLM、Ollama(OpenAI 兼容模式) 等服务。
 */
@Component
public class OpenAiLlmClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    /**
     * 流式增量回调。
     */
    public interface StreamListener {
        void onDelta(String delta) throws Exception;
    }

    private final RobotConfig robotConfig;
    private CloseableHttpClient httpClient;

    @Autowired
    public OpenAiLlmClient(RobotConfig robotConfig) {
        this.robotConfig = robotConfig;
    }

    @PostConstruct
    public void init() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .build();
        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(50)
                .build();
        LOG.info("大模型客户端已初始化");
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * 调用大模型。stream=true 时逐段回调 listener；无论是否流式，都返回完整回答文本。
     */
    public String chat(List<LlmMessage> messages, StreamListener listener) throws LlmException {
        RobotConfig.Llm llm = robotConfig.getLlm();
        if (llm.getModel() == null || llm.getModel().trim().isEmpty()) {
            throw new LlmException("未配置大模型名称 robot.llm.model");
        }

        HttpPost post = new HttpPost(resolveUrl());
        post.setConfig(RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(Math.max(1, llm.getTimeoutSeconds()) * 1000)
                .build());
        post.setHeader("Content-Type", "application/json; charset=utf-8");
        post.setHeader("Accept", llm.isStream() ? "text/event-stream" : "application/json");
        String apiKey = llm.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            post.setHeader("Authorization", "Bearer " + apiKey.trim());
        }
        post.setEntity(new StringEntity(buildRequestJson(messages), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            if (statusCode < 200 || statusCode >= 300) {
                String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
                throw new LlmException("大模型接口返回错误(" + statusCode + "): " + truncate(body));
            }
            if (entity == null) {
                throw new LlmException("大模型接口返回内容为空");
            }
            if (llm.isStream()) {
                return readStream(entity, listener);
            }
            return readFullResponse(entity);
        } catch (LlmException e) {
            throw e;
        } catch (IOException e) {
            throw new LlmException("请求大模型失败: " + e.getMessage(), e);
        }
    }

    private String buildRequestJson(List<LlmMessage> messages) throws LlmException {
        RobotConfig.Llm llm = robotConfig.getLlm();
        JsonObject request = new JsonObject();
        request.addProperty("model", llm.getModel().trim());
        JsonArray messageArray = new JsonArray();
        for (LlmMessage message : messages) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("role", message.getRole());
            item.addProperty("content", message.getContent());
            messageArray.add(item);
        }
        request.add("messages", messageArray);
        request.addProperty("stream", llm.isStream());
        request.addProperty("temperature", llm.getTemperature());
        if (llm.getMaxTokens() > 0) {
            request.addProperty("max_tokens", llm.getMaxTokens());
        }
        return request.toString();
    }

    /**
     * 解析 SSE 流：每行形如 "data: {...}"，以 "data: [DONE]" 结束。
     */
    private String readStream(HttpEntity entity, StreamListener listener) throws LlmException {
        StringBuilder fullText = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                String delta = parseStreamDelta(data);
                if (delta == null || delta.isEmpty()) {
                    continue;
                }
                fullText.append(delta);
                if (listener != null) {
                    try {
                        listener.onDelta(delta);
                    } catch (Exception e) {
                        throw new LlmException("处理流式回复失败: " + e.getMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            throw new LlmException("读取大模型流式响应失败: " + e.getMessage(), e);
        }
        return fullText.toString();
    }

    private String parseStreamDelta(String data) throws LlmException {
        try {
            JsonElement element = JsonParser.parseString(data);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject root = element.getAsJsonObject();
            if (root.has("error")) {
                throw new LlmException("大模型返回错误: " + root.get("error"));
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return null;
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject delta = choice.has("delta") && choice.get("delta").isJsonObject()
                    ? choice.getAsJsonObject("delta") : null;
            if (delta != null && delta.has("content") && !delta.get("content").isJsonNull()) {
                return delta.get("content").getAsString();
            }
            return null;
        } catch (JsonParseException e) {
            throw new LlmException("解析大模型流式响应失败: " + truncate(data), e);
        }
    }

    private String readFullResponse(HttpEntity entity) throws LlmException {
        String body;
        try {
            body = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("读取大模型响应失败: " + e.getMessage(), e);
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                throw new LlmException("大模型返回错误: " + root.get("error"));
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new LlmException("大模型响应中没有 choices: " + truncate(body));
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
                throw new LlmException("大模型响应中没有消息内容: " + truncate(body));
            }
            return message.get("content").getAsString();
        } catch (JsonParseException e) {
            throw new LlmException("解析大模型响应失败: " + truncate(body), e);
        }
    }

    String resolveUrl() {
        String baseUrl = robotConfig.getLlm().getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "https://api.deepseek.com";
        }
        baseUrl = baseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith(CHAT_COMPLETIONS_PATH)) {
            return baseUrl;
        }
        return baseUrl + CHAT_COMPLETIONS_PATH;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_ERROR_BODY_LENGTH ? text : text.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
