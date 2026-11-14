package cn.wildfirechat.app.chat;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.llm.LlmMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话历史记录服务：负责落地聊天记录，并按配置的条数加载最近的历史。
 */
@Service
public class ChatHistoryService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final int MAX_CONTENT_CHARS = 8000;

    private final ChatMessageRepository repository;
    private final RobotConfig robotConfig;

    @Autowired
    public ChatHistoryService(ChatMessageRepository repository, RobotConfig robotConfig) {
        this.repository = repository;
        this.robotConfig = robotConfig;
    }

    public boolean saveUserMessage(int convType, String convTarget, String sender, String content) {
        return save(convType, convTarget, sender, ChatMessage.ROLE_USER, content);
    }

    public void saveAssistantMessage(int convType, String convTarget, String content) {
        String robotId = robotConfig.getIm_id();
        save(convType, convTarget, robotId, ChatMessage.ROLE_ASSISTANT, content);
    }

    private boolean save(int convType, String convTarget, String sender, String role, String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS);
        }
        ChatMessage message = new ChatMessage();
        message.setConvType(convType);
        message.setConvTarget(convTarget);
        message.setSender(sender);
        message.setRole(role);
        message.setContent(content);
        message.setSendTime(System.currentTimeMillis());
        return repository.insert(message);
    }

    /**
     * 加载某个会话最近 historyLimit 条消息（包含用户与机器人的消息）。
     * 数据库异常时降级为空列表，保证机器人仍能回答当前问题。
     */
    public List<LlmMessage> loadHistory(int convType, String convTarget) {
        int limit = robotConfig.getLlm().getHistoryLimit();
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<ChatMessage> records;
        try {
            records = repository.findRecent(convType, convTarget, limit);
        } catch (Exception e) {
            LOG.error("加载会话历史失败, convType:{}, convTarget:{}", convType, convTarget, e);
            return Collections.emptyList();
        }
        List<LlmMessage> history = new ArrayList<>(records.size());
        for (ChatMessage record : records) {
            if (ChatMessage.ROLE_USER.equals(record.getRole())) {
                history.add(new LlmMessage(LlmMessage.ROLE_USER, record.getContent()));
            } else if (ChatMessage.ROLE_ASSISTANT.equals(record.getRole())) {
                history.add(new LlmMessage(LlmMessage.ROLE_ASSISTANT, record.getContent()));
            }
        }
        return history;
    }
}
