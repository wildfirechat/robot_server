package cn.wildfirechat.app.rag;

import cn.wildfirechat.app.RobotConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OpenAI 兼容的 /embeddings 客户端。
 * 地址/Key 留空时复用大模型配置。
 */
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SOCKET_TIMEOUT_MS = 60_000;

    private final RobotConfig robotConfig;
    private CloseableHttpClient httpClient;

    public OpenAiEmbeddingClient(RobotConfig robotConfig) {
        this.robotConfig = robotConfig;
    }

    @PostConstruct
    public void init() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();
        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .build();
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Override
    public float[] embedOne(String text) throws Exception {
        List<float[]> result = embedAll(java.util.Collections.singletonList(text));
        if (result.isEmpty()) {
            throw new IllegalStateException("Embedding 接口没有返回向量");
        }
        return result.get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        RobotConfig.Rag rag = robotConfig.getRag();
        String model = rag.getEmbeddingModel();
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("未配置 Embedding 模型");
        }
        String baseUrl = StringUtils.hasText(rag.getEmbeddingBaseUrl())
                ? rag.getEmbeddingBaseUrl() : robotConfig.getLlm().getBaseUrl();
        String apiKey = StringUtils.hasText(rag.getEmbeddingApiKey())
                ? rag.getEmbeddingApiKey() : robotConfig.getLlm().getApiKey();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("未配置 Embedding 接口地址");
        }

        JsonObject request = new JsonObject();
        request.addProperty("model", model.trim());
        JsonArray input = new JsonArray();
        for (String text : texts) {
            input.add(text == null ? "" : text);
        }
        request.add("input", input);

        HttpPost post = new HttpPost(resolveUrl(baseUrl.trim()));
        post.setHeader("Content-Type", "application/json; charset=utf-8");
        if (StringUtils.hasText(apiKey)) {
            post.setHeader("Authorization", "Bearer " + apiKey.trim());
        }
        post.setEntity(new StringEntity(request.toString(), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Embedding 接口返回错误(" + status + "): " + truncate(body));
            }
            return parse(body, texts.size());
        } catch (IOException e) {
            throw new IllegalStateException("请求 Embedding 接口失败: " + e.getMessage(), e);
        }
    }

    private static List<float[]> parse(String body, int expected) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (root.has("error")) {
            throw new IllegalStateException("Embedding 接口返回错误: " + root.get("error"));
        }
        JsonArray data = root.getAsJsonArray("data");
        if (data == null || data.size() != expected) {
            throw new IllegalStateException("Embedding 返回数量不匹配，期望 " + expected + "，实际 "
                    + (data == null ? 0 : data.size()));
        }
        List<IndexedVector> vectors = new ArrayList<>(data.size());
        for (JsonElement element : data) {
            JsonObject item = element.getAsJsonObject();
            int index = item.has("index") ? item.get("index").getAsInt() : vectors.size();
            JsonArray embedding = item.getAsJsonArray("embedding");
            if (embedding == null || embedding.size() == 0) {
                throw new IllegalStateException("Embedding 返回空向量");
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).getAsFloat();
            }
            vectors.add(new IndexedVector(index, vector));
        }
        vectors.sort(Comparator.comparingInt(v -> v.index));
        List<float[]> result = new ArrayList<>(vectors.size());
        for (IndexedVector vector : vectors) {
            result.add(vector.vector);
        }
        return result;
    }

    static String resolveUrl(String baseUrl) {
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/embeddings")) {
            return base;
        }
        return base + "/embeddings";
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    private static class IndexedVector {
        private final int index;
        private final float[] vector;

        private IndexedVector(int index, float[] vector) {
            this.index = index;
            this.vector = vector;
        }
    }
}
