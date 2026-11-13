package cn.wildfirechat.app.llm;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.chat.ChatHistoryService;
import cn.wildfirechat.app.chat.ChatMessage;
import cn.wildfirechat.app.chat.ChatMessageRepository;
import cn.wildfirechat.app.im.MessageSender;
import cn.wildfirechat.app.stats.LlmCallRepository;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmReplyServiceTest {
    private static final int STREAM_GENERATING_TYPE = 14;
    private static final int STREAM_GENERATED_TYPE = 15;

    private TestLlmServer server;
    private RobotConfig config;
    private OpenAiLlmClient client;
    private ChatMessageRepository repository;
    private LlmCallRepository llmCallRepository;
    private LlmReplyService replyService;
    private RecordingSender sender;

    @BeforeEach
    public void setUp() throws Exception {
        server = new TestLlmServer();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:llm_reply_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new ChatMessageRepository(jdbcTemplate);
        llmCallRepository = new LlmCallRepository(jdbcTemplate);

        config = new RobotConfig();
        config.setIm_id("FireRobot");
        config.getLlm().setEnabled(true);
        config.getLlm().setBaseUrl(server.baseUrl());
        config.getLlm().setApiKey("test-key");
        config.getLlm().setModel("test-model");
        config.getLlm().setSystemPrompt("你是助手");
        config.getLlm().setHistoryLimit(30);
        config.getLlm().setStreamIntervalMs(60_000);
        config.getLlm().setStream(true);

        client = new OpenAiLlmClient(config);
        client.init();

        ChatHistoryService historyService = new ChatHistoryService(repository, config);
        replyService = new LlmReplyService(client, historyService, config, llmCallRepository);
        sender = new RecordingSender();
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.destroy();
        server.close();
    }

    @Test
    public void replySendsStreamingMessagesWithLast30History() throws Exception {
        insertHistory(40);
        server.responseBody = "data: {\"choices\":[{\"delta\":{\"content\":\"答\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"案\"}}]}\n\n"
                + "data: [DONE]\n\n";

        replyService.reply(conversation(), "user1", "现在的问题", 123L, sender);

        // 1 条 system + 最近 30 条历史（包含刚落库的当前消息）
        JsonObject request = JsonParser.parseString(server.requestBodies.get(0)).getAsJsonObject();
        JsonArray messages = request.getAsJsonArray("messages");
        assertEquals(31, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("历史11", messages.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("user", messages.get(30).getAsJsonObject().get("role").getAsString());
        assertEquals("现在的问题", messages.get(30).getAsJsonObject().get("content").getAsString());

        // 第一次增量立即推送，第二次因未到间隔被合并，最后发送结束消息
        assertEquals(2, sender.messages.size());
        Sent first = sender.messages.get(0);
        assertEquals(123L, first.replyMessageId);
        assertEquals(STREAM_GENERATING_TYPE, first.payload.getType());
        assertEquals("答", first.payload.getSearchableContent());

        Sent last = sender.messages.get(1);
        assertEquals(123L, last.replyMessageId);
        assertEquals(STREAM_GENERATED_TYPE, last.payload.getType());
        assertEquals("答案", last.payload.getSearchableContent());
        assertEquals(first.payload.getContent(), last.payload.getContent());

        // 机器人的回答落库，成为下一轮上下文
        List<ChatMessage> recent = repository.findRecent(0, "user1", 1);
        assertEquals(1, recent.size());
        assertEquals(ChatMessage.ROLE_ASSISTANT, recent.get(0).getRole());
        assertEquals("答案", recent.get(0).getContent());
        assertEquals(1, llmCallRepository.count());
    }

    @Test
    public void replyFallsBackToTextWhenLlmFails() throws Exception {
        server.status = 500;
        server.contentType = "application/json; charset=utf-8";
        server.responseBody = "{\"error\":{\"message\":\"boom\"}}";

        replyService.reply(conversation(), "user1", "现在的问题", 0, sender);

        assertEquals(1, sender.messages.size());
        MessagePayload payload = sender.messages.get(0).payload;
        assertEquals(1, payload.getType());
        assertTrue(payload.getSearchableContent().contains("抱歉"), payload.getSearchableContent());

        List<ChatMessage> recent = repository.findRecent(0, "user1", 1);
        assertEquals(1, recent.size());
        assertEquals(ChatMessage.ROLE_USER, recent.get(0).getRole());
        assertEquals("现在的问题", recent.get(0).getContent());
    }

    @Test
    public void replyFallsBackToTextWhenLlmReturnsEmptyContent() throws Exception {
        server.responseBody = "data: [DONE]\n\n";

        replyService.reply(conversation(), "user1", "现在的问题", 0, sender);

        assertEquals(1, sender.messages.size());
        assertEquals(1, sender.messages.get(0).payload.getType());
        assertTrue(sender.messages.get(0).payload.getSearchableContent().contains("抱歉"));
        assertEquals(1, repository.findRecent(0, "user1", 5).size());
    }

    @Test
    public void disabledLlmRepliesHintWithoutCallingServer() throws Exception {
        config.getLlm().setEnabled(false);

        replyService.reply(conversation(), "user1", "现在的问题", 0, sender);

        assertTrue(server.requestBodies.isEmpty());
        assertEquals(1, sender.messages.size());
        assertTrue(sender.messages.get(0).payload.getSearchableContent().contains("未开启"));
        assertTrue(repository.findRecent(0, "user1", 5).isEmpty());
    }

    @Test
    public void longInputAndHistoryAreTrimmedBeforeCallingLlm() throws Exception {
        for (int i = 0; i < 40; i++) {
            ChatMessage message = new ChatMessage();
            message.setConvType(0);
            message.setConvTarget("user1");
            message.setSender(i % 2 == 0 ? "user1" : "FireRobot");
            message.setRole(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT);
            message.setContent("历史" + i + "x".repeat(1000));
            message.setSendTime(i);
            assertTrue(repository.insert(message));
        }
        server.responseBody = "data: {\"choices\":[{\"delta\":{\"content\":\"答\"}}]}\n\n"
                + "data: [DONE]\n\n";

        String longInput = "y".repeat(10000);
        replyService.reply(conversation(), "user1", longInput, 0, sender);

        JsonObject request = JsonParser.parseString(server.requestBodies.get(0)).getAsJsonObject();
        JsonArray messages = request.getAsJsonArray("messages");
        int promptChars = 0;
        for (JsonElement element : messages) {
            promptChars += element.getAsJsonObject().get("content").getAsString().length();
        }
        assertTrue(promptChars <= 32000, "promptChars=" + promptChars);
        JsonObject last = messages.get(messages.size() - 1).getAsJsonObject();
        assertEquals("user", last.get("role").getAsString());
        assertEquals(8000, last.get("content").getAsString().length());

        List<ChatMessage> recent = repository.findRecent(0, "user1", 2);
        assertEquals(2, recent.size());
        assertEquals(ChatMessage.ROLE_USER, recent.get(0).getRole());
        assertEquals(8000, recent.get(0).getContent().length());
    }

    private Conversation conversation() {
        return new Conversation(0, "user1", 0);
    }

    private void insertHistory(int count) {
        for (int i = 0; i < count; i++) {
            ChatMessage message = new ChatMessage();
            message.setConvType(0);
            message.setConvTarget("user1");
            message.setSender(i % 2 == 0 ? "user1" : "FireRobot");
            message.setRole(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT);
            message.setContent("历史" + i);
            message.setSendTime(i);
            assertTrue(repository.insert(message));
        }
    }

    private static class Sent {
        final Conversation conversation;
        final long replyMessageId;
        final MessagePayload payload;

        Sent(Conversation conversation, long replyMessageId, MessagePayload payload) {
            this.conversation = conversation;
            this.replyMessageId = replyMessageId;
            this.payload = payload;
        }
    }

    private static class RecordingSender implements MessageSender {
        final List<Sent> messages = new ArrayList<>();

        @Override
        public void send(Conversation conversation, long replyMessageId, MessagePayload payload) {
            messages.add(new Sent(conversation, replyMessageId, payload));
        }
    }
}
