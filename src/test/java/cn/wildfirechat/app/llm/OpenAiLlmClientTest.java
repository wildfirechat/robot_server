package cn.wildfirechat.app.llm;

import cn.wildfirechat.app.RobotConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class OpenAiLlmClientTest {
    private TestLlmServer server;
    private RobotConfig config;
    private OpenAiLlmClient client;

    @BeforeEach
    public void setUp() throws Exception {
        server = new TestLlmServer();
        config = new RobotConfig();
        config.getLlm().setBaseUrl(server.baseUrl());
        config.getLlm().setApiKey("test-key");
        config.getLlm().setModel("test-model");
        config.getLlm().setTimeoutSeconds(10);
        client = new OpenAiLlmClient(config);
        client.init();
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.destroy();
        server.close();
    }

    @Test
    public void streamChatCollectsDeltasAndBuildsRequest() throws Exception {
        server.responseBody = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"，世界！\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";

        final List<String> deltas = new ArrayList<>();
        String text = client.chat(Arrays.asList(
                new LlmMessage(LlmMessage.ROLE_SYSTEM, "你是助手"),
                new LlmMessage(LlmMessage.ROLE_USER, "hello")), deltas::add);

        assertEquals("你好，世界！", text);
        assertEquals(Arrays.asList("你好", "，世界！"), deltas);
        assertEquals("/v1/chat/completions", server.requestPaths.get(0));
        assertEquals("Bearer test-key", server.authorizationHeaders.get(0));

        JsonObject request = JsonParser.parseString(server.requestBodies.get(0)).getAsJsonObject();
        assertEquals("test-model", request.get("model").getAsString());
        assertTrue(request.get("stream").getAsBoolean());
        assertEquals(0.7, request.get("temperature").getAsDouble(), 0.0001);
        JsonArray messages = request.getAsJsonArray("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("hello", messages.get(1).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void nonStreamChatReturnsFullAnswer() throws Exception {
        config.getLlm().setStream(false);
        server.contentType = "application/json; charset=utf-8";
        server.responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"完整回答\"}}]}";

        final List<String> deltas = new ArrayList<>();
        String text = client.chat(Arrays.asList(new LlmMessage(LlmMessage.ROLE_USER, "hi")), deltas::add);

        assertEquals("完整回答", text);
        assertTrue(deltas.isEmpty());
        JsonObject request = JsonParser.parseString(server.requestBodies.get(0)).getAsJsonObject();
        assertFalse(request.get("stream").getAsBoolean());
    }

    @Test
    public void httpErrorIsReported() throws Exception {
        server.status = 401;
        server.contentType = "application/json; charset=utf-8";
        server.responseBody = "{\"error\":{\"message\":\"invalid api key\"}}";
        try {
            client.chat(Arrays.asList(new LlmMessage(LlmMessage.ROLE_USER, "hi")), null);
            fail("应当抛出 LlmException");
        } catch (LlmException e) {
            assertTrue(e.getMessage().contains("401"), e.getMessage());
            assertTrue(e.getMessage().contains("invalid api key"), e.getMessage());
        }
    }

    @Test
    public void streamErrorMessageIsReported() throws Exception {
        server.responseBody = "data: {\"error\":{\"message\":\"rate limit\"}}\n\n";
        try {
            client.chat(Arrays.asList(new LlmMessage(LlmMessage.ROLE_USER, "hi")), null);
            fail("应当抛出 LlmException");
        } catch (LlmException e) {
            assertTrue(e.getMessage().contains("rate limit"), e.getMessage());
        }
    }

    @Test
    public void baseUrlWithChatCompletionsPathIsKept() {
        config.getLlm().setBaseUrl("https://example.com/v1/chat/completions/");
        assertEquals("https://example.com/v1/chat/completions", client.resolveUrl());
        config.getLlm().setBaseUrl("https://example.com/v1/");
        assertEquals("https://example.com/v1/chat/completions", client.resolveUrl());
    }

    @Test
    public void defaultConfigUsesDeepSeek() {
        RobotConfig defaults = new RobotConfig();
        assertEquals("https://api.deepseek.com", defaults.getLlm().getBaseUrl());
        assertEquals("deepseek-chat", defaults.getLlm().getModel());

        config.getLlm().setBaseUrl("");
        assertEquals("https://api.deepseek.com/chat/completions", client.resolveUrl());
    }
}
