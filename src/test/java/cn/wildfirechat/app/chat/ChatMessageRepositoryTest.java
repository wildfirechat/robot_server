package cn.wildfirechat.app.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChatMessageRepositoryTest {
    private ChatMessageRepository repository;

    @BeforeEach
    public void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:chat_repo_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        repository = new ChatMessageRepository(new JdbcTemplate(dataSource));
    }

    @Test
    public void findRecentReturnsLatestMessagesInAscendingOrder() {
        for (int i = 0; i < 35; i++) {
            insert(0, "user1", i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT, "消息" + i, i);
        }
        insert(0, "user2", ChatMessage.ROLE_USER, "其他会话", 100);

        List<ChatMessage> recent = repository.findRecent(0, "user1", 30);

        assertEquals(30, recent.size());
        assertEquals("消息5", recent.get(0).getContent());
        assertEquals("消息34", recent.get(29).getContent());
        assertEquals(5, recent.get(0).getSendTime());
        assertEquals(34, recent.get(29).getSendTime());
    }

    @Test
    public void findRecentIgnoresOtherConversations() {
        insert(0, "user1", ChatMessage.ROLE_USER, "私聊1", 1);
        insert(1, "group1", ChatMessage.ROLE_USER, "群聊1", 2);
        insert(0, "user2", ChatMessage.ROLE_USER, "别人", 3);

        List<ChatMessage> privateMessages = repository.findRecent(0, "user1", 10);
        List<ChatMessage> groupMessages = repository.findRecent(1, "group1", 10);

        assertEquals(1, privateMessages.size());
        assertEquals("私聊1", privateMessages.get(0).getContent());
        assertEquals(1, groupMessages.size());
        assertEquals("群聊1", groupMessages.get(0).getContent());
    }

    @Test
    public void longContentIsStoredCompletely() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            content.append("很长的回答");
        }
        insert(0, "user1", ChatMessage.ROLE_ASSISTANT, content.toString(), 1);

        List<ChatMessage> messages = repository.findRecent(0, "user1", 1);
        assertEquals(1, messages.size());
        assertEquals(content.toString(), messages.get(0).getContent());
        assertTrue(messages.get(0).getContent().length() > 4000);
    }

    @Test
    public void findRecentWithZeroLimitReturnsEmpty() {
        insert(0, "user1", ChatMessage.ROLE_USER, "消息", 1);
        assertTrue(repository.findRecent(0, "user1", 0).isEmpty());
    }

    private void insert(int convType, String convTarget, String role, String content, long time) {
        ChatMessage message = new ChatMessage();
        message.setConvType(convType);
        message.setConvTarget(convTarget);
        message.setSender("sender");
        message.setRole(role);
        message.setContent(content);
        message.setSendTime(time);
        assertTrue(repository.insert(message));
    }
}
