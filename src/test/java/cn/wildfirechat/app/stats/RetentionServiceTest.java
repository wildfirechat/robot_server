package cn.wildfirechat.app.stats;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.chat.ChatMessage;
import cn.wildfirechat.app.chat.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RetentionServiceTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private JdbcTemplate jdbcTemplate;
    private ChatMessageRepository chatMessageRepository;
    private LlmCallRepository llmCallRepository;
    private RobotConfig robotConfig;
    private RetentionService retentionService;

    @BeforeEach
    public void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:retention_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        chatMessageRepository = new ChatMessageRepository(jdbcTemplate);
        llmCallRepository = new LlmCallRepository(jdbcTemplate);
        robotConfig = new RobotConfig();
        retentionService = new RetentionService(robotConfig, chatMessageRepository, llmCallRepository);
    }

    @Test
    public void deletesRecordsOlderThanRetentionDays() {
        long now = System.currentTimeMillis();
        insertChat("old", now - 40 * DAY);
        insertChat("new", now - 1 * DAY);
        llmCallRepository.insert(0, "user1", "user1", "model", now - 40 * DAY, 100, true, null, 1, 1);
        llmCallRepository.insert(0, "user1", "user1", "model", now - 1 * DAY, 100, true, null, 1, 1);

        robotConfig.setRetentionDays(30);
        retentionService.cleanup();

        assertEquals(1L, count("SELECT COUNT(*) FROM chat_message"));
        assertEquals(1L, count("SELECT COUNT(*) FROM llm_call"));
        assertEquals("new", jdbcTemplate.queryForObject("SELECT content FROM chat_message", String.class));
    }

    @Test
    public void zeroRetentionDisablesCleanup() {
        long now = System.currentTimeMillis();
        insertChat("old", now - 400 * DAY);
        robotConfig.setRetentionDays(0);

        retentionService.cleanup();

        assertEquals(1L, count("SELECT COUNT(*) FROM chat_message"));
    }

    private void insertChat(String content, long sendTime) {
        ChatMessage message = new ChatMessage();
        message.setConvType(0);
        message.setConvTarget("user1");
        message.setSender("user1");
        message.setRole(ChatMessage.ROLE_USER);
        message.setContent(content);
        message.setSendTime(sendTime);
        chatMessageRepository.insert(message);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
