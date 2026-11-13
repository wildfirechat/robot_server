package cn.wildfirechat.app.stats;

import cn.wildfirechat.app.chat.ChatMessage;
import cn.wildfirechat.app.chat.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatsServiceTest {
    private ChatMessageRepository messageRepository;
    private LlmCallRepository llmCallRepository;
    private StatsService statsService;

    @BeforeEach
    public void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:stats_service_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        messageRepository = new ChatMessageRepository(jdbcTemplate);
        llmCallRepository = new LlmCallRepository(jdbcTemplate);
        statsService = new StatsService(new StatsRepository(jdbcTemplate));
    }

    @Test
    public void aggregatesDailyTotalsAndConversations() {
        long now = System.currentTimeMillis();
        long eightDaysAgo = LocalDateTime.now().minusDays(8)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        insertMessage(0, "user1", ChatMessage.ROLE_USER, "你好", now - 3000);
        insertMessage(0, "user1", ChatMessage.ROLE_ASSISTANT, "你好呀", now - 2000);
        insertMessage(1, "group1", ChatMessage.ROLE_USER, "8天前的消息", eightDaysAgo);

        llmCallRepository.insert(0, "user1", "user1", "deepseek-chat", now - 2500, 100, true, null, 10, 20);
        llmCallRepository.insert(0, "user1", "user1", "deepseek-chat", now - 1500, 300, false, "failed", 10, 0);
        llmCallRepository.insert(1, "group1", "user2", "deepseek-chat", eightDaysAgo, 200, true, null, 5, 5);

        DashboardStats stats = statsService.load();

        assertEquals(3, stats.getTotalMessages());
        assertEquals(1, stats.getTodayUserMessages());
        assertEquals(1, stats.getTodayAssistantMessages());
        assertEquals(2, stats.getTodayMessages());
        assertEquals(2, stats.getTotalConversations());

        assertEquals(3, stats.getTotalLlmCalls());
        assertEquals(2, stats.getTodayLlmCalls());
        assertEquals(1, stats.getTotalLlmFailures());
        assertEquals(1, stats.getTodayLlmFailures());
        assertEquals(67, stats.getLlmSuccessRate());
        assertEquals(200, stats.getAvgLlmDurationMs());

        List<DailyStat> days = stats.getDailyStats();
        assertEquals(7, days.size());
        DailyStat today = days.get(days.size() - 1);
        assertEquals(LocalDate.now(), today.getDate());
        assertEquals(2, today.getTotalMessages());
        assertEquals(2, today.getLlmCalls());
        assertEquals(1, today.getLlmFailures());
        assertEquals(100, today.getMessagePercent());

        assertEquals(2, stats.getRecentConversations().size());
        assertEquals("user1", stats.getRecentConversations().get(0).getConvTarget());
        assertEquals(3, stats.getRecentMessages().size());
    }

    @Test
    public void previewTruncatesAndFlattensContent() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            content.append("很长的回答\n");
        }
        insertMessage(0, "user1", ChatMessage.ROLE_ASSISTANT, content.toString(), System.currentTimeMillis());

        String preview = statsService.load().getRecentMessages().get(0).getPreview();
        assertTrue(preview.length() <= 81);
        assertTrue(preview.endsWith("…"));
        assertFalse(preview.contains("\n"));
    }

    private void insertMessage(int convType, String convTarget, String role, String content, long sendTime) {
        ChatMessage message = new ChatMessage();
        message.setConvType(convType);
        message.setConvTarget(convTarget);
        message.setSender(role.equals(ChatMessage.ROLE_USER) ? "user1" : "FireRobot");
        message.setRole(role);
        message.setContent(content);
        message.setSendTime(sendTime);
        assertTrue(messageRepository.insert(message));
    }
}
