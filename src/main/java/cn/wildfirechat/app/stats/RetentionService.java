package cn.wildfirechat.app.stats;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.chat.ChatMessageRepository;
import cn.wildfirechat.app.rag.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 按配置的保留天数清理聊天记录和大模型调用记录。
 * 保留天数在管理后台配置，0 表示关闭自动清理。
 */
@Service
public class RetentionService {
    private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final RobotConfig robotConfig;
    private final ChatMessageRepository chatMessageRepository;
    private final LlmCallRepository llmCallRepository;
    private final KnowledgeRepository knowledgeRepository;

    public RetentionService(RobotConfig robotConfig, ChatMessageRepository chatMessageRepository,
                            LlmCallRepository llmCallRepository, KnowledgeRepository knowledgeRepository) {
        this.robotConfig = robotConfig;
        this.chatMessageRepository = chatMessageRepository;
        this.llmCallRepository = llmCallRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        int retentionDays = robotConfig.getRetentionDays();
        if (retentionDays <= 0) {
            LOG.debug("未开启数据保留策略，跳过清理");
            return;
        }
        long cutoff = System.currentTimeMillis() - retentionDays * DAY_MILLIS;
        try {
            int chats = chatMessageRepository.deleteBefore(cutoff);
            int calls = llmCallRepository.deleteBefore(cutoff);
            int ragLogs = knowledgeRepository.deleteQueryLogsBefore(cutoff);
            LOG.info("已清理 {} 天前的数据: chat_message {} 条, llm_call {} 条, rag_query_log {} 条",
                    retentionDays, chats, calls, ragLogs);
        } catch (Exception e) {
            LOG.error("清理历史数据失败", e);
        }
    }
}
