package cn.wildfirechat.app.llm;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.chat.ChatHistoryService;
import cn.wildfirechat.app.im.ImStreamingSender;
import cn.wildfirechat.app.im.MessageSender;
import cn.wildfirechat.app.stats.LlmCallRepository;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 大模型回复服务：
 * 1. 保存用户消息到数据库；
 * 2. 查询该会话最近 N 条历史（N 可在管理后台配置，默认 30）；
 * 3. 组装 system + 历史消息调用大模型；
 * 4. 用流式文本消息把结果回复给用户；
 * 5. 保存机器人的回答，形成下一轮对话的上下文；
 * 6. 记录本次大模型调用的耗时和结果，供管理后台统计。
 */
@Service
public class LlmReplyService {
    private static final Logger LOG = LoggerFactory.getLogger(LlmReplyService.class);

    private static final String DISABLED_REPLY = "AI 回复功能尚未开启，请联系管理员在管理后台开启。";
    private static final String UNAVAILABLE_REPLY = "抱歉，AI 服务暂时不可用，请稍后再试。";
    private static final String EMPTY_QUESTION_REPLY = "你想问点什么呢？";

    /** 单条用户消息最大字符数 */
    private static final int MAX_USER_TEXT_CHARS = 8000;
    /** 单次请求的 system + 历史 + 当前消息总字符数上限 */
    private static final int MAX_PROMPT_CHARS = 32000;
    /** 会话串行化锁数量，避免同一会话并发调用大模型 */
    private static final int CONVERSATION_LOCK_COUNT = 64;

    private final OpenAiLlmClient llmClient;
    private final ChatHistoryService chatHistoryService;
    private final RobotConfig robotConfig;
    private final LlmCallRepository llmCallRepository;
    private final ReentrantLock[] conversationLocks = new ReentrantLock[CONVERSATION_LOCK_COUNT];

    @Autowired
    public LlmReplyService(OpenAiLlmClient llmClient, ChatHistoryService chatHistoryService,
                           RobotConfig robotConfig, LlmCallRepository llmCallRepository) {
        this.llmClient = llmClient;
        this.chatHistoryService = chatHistoryService;
        this.robotConfig = robotConfig;
        this.llmCallRepository = llmCallRepository;
        for (int i = 0; i < conversationLocks.length; i++) {
            conversationLocks[i] = new ReentrantLock(true);
        }
    }

    /**
     * 使用大模型回复用户消息。
     *
     * @param conversation  会话（私聊时 target 为对方用户ID，群聊时为群ID）
     * @param sender        发消息的用户ID
     * @param userText      用户消息内容（已去掉 @机器人 的前缀）
     * @param replyMessageId 需要以回复方式发送时传入原消息ID，否则传 0
     * @param messageSender IM 消息发送器
     */
    public void reply(Conversation conversation, String sender, String userText,
                      long replyMessageId, MessageSender messageSender) {
        RobotConfig.Llm llm = robotConfig.getLlm();
        if (!llm.isEnabled()) {
            LOG.info("大模型回复未开启，忽略本次消息");
            sendText(messageSender, conversation, replyMessageId, DISABLED_REPLY);
            return;
        }
        if (userText == null || userText.trim().isEmpty()) {
            sendText(messageSender, conversation, replyMessageId, EMPTY_QUESTION_REPLY);
            return;
        }

        String text = limitUserText(userText);
        int convType = conversation.getType();
        String convTarget = conversation.getTarget();
        ReentrantLock lock = conversationLock(convType, convTarget);
        lock.lock();
        try {
            doReply(conversation, sender, text, replyMessageId, messageSender, llm, convType, convTarget);
        } finally {
            lock.unlock();
        }
    }

    private void doReply(Conversation conversation, String sender, String userText, long replyMessageId,
                         MessageSender messageSender, RobotConfig.Llm llm, int convType, String convTarget) {
        boolean saved = chatHistoryService.saveUserMessage(convType, convTarget, sender, userText);
        List<LlmMessage> messages = buildMessages(convType, convTarget, userText, saved);
        messages = trimPrompt(messages);
        LOG.info("调用大模型, convType:{}, convTarget:{}, 携带消息数:{}, prompt字符数:{}",
                convType, convTarget, messages.size(), countChars(messages));

        long startedAt = System.currentTimeMillis();
        String model = llm.getModel();
        int promptChars = countChars(messages);
        boolean success = false;
        String error = null;

        ImStreamingSender streamingSender = new ImStreamingSender(
                messageSender, conversation, replyMessageId, llm.getStreamIntervalMs());
        try {
            String answer = llmClient.chat(messages, streamingSender);
            if (!llm.isStream() && answer != null && !answer.isEmpty()) {
                streamingSender.onDelta(answer);
            }
            if (streamingSender.getFullText().isEmpty()) {
                error = "大模型返回内容为空";
                LOG.warn("大模型返回内容为空, convTarget:{}", convTarget);
                sendText(messageSender, conversation, replyMessageId, UNAVAILABLE_REPLY);
                return;
            }
            streamingSender.finish();
            chatHistoryService.saveAssistantMessage(convType, convTarget, streamingSender.getFullText());
            success = true;
        } catch (Exception e) {
            error = e.getMessage();
            LOG.error("大模型回复失败, convType:{}, convTarget:{}", convType, convTarget, e);
            if (streamingSender.hasSent()) {
                // 已输出部分内容，补发结束消息，避免客户端一直显示「生成中」
                try {
                    streamingSender.finish();
                } catch (Exception finishError) {
                    LOG.warn("发送流式结束消息失败: {}", finishError.getMessage());
                }
            } else {
                sendText(messageSender, conversation, replyMessageId, UNAVAILABLE_REPLY);
            }
        } finally {
            llmCallRepository.insert(convType, convTarget, sender, model, startedAt,
                    System.currentTimeMillis() - startedAt, success, error,
                    promptChars, streamingSender.getFullText().length());
        }
    }

    private ReentrantLock conversationLock(int convType, String convTarget) {
        int hash = (convType + "|" + (convTarget == null ? "" : convTarget)).hashCode();
        return conversationLocks[Math.floorMod(hash, conversationLocks.length)];
    }

    private static String limitUserText(String userText) {
        if (userText.length() <= MAX_USER_TEXT_CHARS) {
            return userText;
        }
        LOG.warn("用户消息过长已截断, 原长度:{}, 上限:{}", userText.length(), MAX_USER_TEXT_CHARS);
        return userText.substring(0, MAX_USER_TEXT_CHARS);
    }

    /**
     * 组装 system + 最近历史消息。历史中包含刚落库的当前用户消息；
     * 若保存失败或历史为空，则至少把当前消息带上。
     */
    private List<LlmMessage> buildMessages(int convType, String convTarget, String userText, boolean userMessageSaved) {
        RobotConfig.Llm llm = robotConfig.getLlm();
        List<LlmMessage> messages = new ArrayList<>();
        if (llm.getSystemPrompt() != null && !llm.getSystemPrompt().trim().isEmpty()) {
            messages.add(new LlmMessage(LlmMessage.ROLE_SYSTEM, llm.getSystemPrompt()));
        }
        List<LlmMessage> history = chatHistoryService.loadHistory(convType, convTarget);
        messages.addAll(history);
        int historyCount = messages.size() - (hasSystemPrompt(llm) ? 1 : 0);
        if (!userMessageSaved || historyCount == 0) {
            messages.add(new LlmMessage(LlmMessage.ROLE_USER, userText));
        }
        return messages;
    }

    /**
     * 裁剪过大的 prompt：保留 system 和当前消息，从最老的历史开始删除。
     */
    private static List<LlmMessage> trimPrompt(List<LlmMessage> messages) {
        int total = countChars(messages);
        if (total <= MAX_PROMPT_CHARS) {
            return messages;
        }
        int start = !messages.isEmpty()
                && LlmMessage.ROLE_SYSTEM.equals(messages.get(0).getRole()) ? 1 : 0;
        int removed = 0;
        while (total > MAX_PROMPT_CHARS && messages.size() > start + 1) {
            LlmMessage message = messages.remove(start);
            total -= contentLength(message);
            removed++;
        }
        if (removed > 0) {
            LOG.warn("历史消息过大，已裁剪 {} 条最老记录, prompt字符数:{}", removed, total);
        }
        return messages;
    }

    private static int countChars(List<LlmMessage> messages) {
        int total = 0;
        for (LlmMessage message : messages) {
            total += contentLength(message);
        }
        return total;
    }

    private static int contentLength(LlmMessage message) {
        return message.getContent() == null ? 0 : message.getContent().length();
    }

    private static boolean hasSystemPrompt(RobotConfig.Llm llm) {
        return llm.getSystemPrompt() != null && !llm.getSystemPrompt().trim().isEmpty();
    }

    private static void sendText(MessageSender messageSender, Conversation conversation,
                                 long replyMessageId, String text) {
        MessagePayload payload = new MessagePayload();
        payload.setType(1);
        payload.setSearchableContent(text);
        try {
            messageSender.send(conversation, replyMessageId, payload);
        } catch (Exception e) {
            LOG.error("发送回复消息失败: {}", e.getMessage(), e);
        }
    }
}
