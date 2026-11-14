package cn.wildfirechat.app;

import cn.wildfirechat.app.chat.ChatHistoryService;
import cn.wildfirechat.app.im.MessageSender;
import cn.wildfirechat.app.im.RobotMessageSender;
import cn.wildfirechat.app.im.RobotServiceProvider;
import cn.wildfirechat.app.llm.LlmReplyService;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.sdk.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    @Autowired
    private RobotConfig mRobotConfig;

    @Autowired
    private LlmReplyService llmReplyService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    private static final long USER_NAME_CACHE_TTL_MS = 10 * 60 * 1000L;
    private final Map<String, CachedName> userNameCache = new ConcurrentHashMap<>();

    private final RobotServiceProvider robotServiceProvider;

    private MessageSender messageSender;

    public ServiceImpl(RobotServiceProvider robotServiceProvider) {
        this.robotServiceProvider = robotServiceProvider;
    }

    @PostConstruct
    private void init() {
        messageSender = new RobotMessageSender(robotServiceProvider, mRobotConfig);
    }

//    int ConversationType_Private = 0;
//    int ConversationType_Group = 1;
//    int ConversationType_ChatRoom = 2;
//    int ConversationType_Channel = 3;
//    int ConversationType_Thing = 4;
    @Override
    @Async("asyncExecutor")
    public void onReceiveMessage(OutputMessageData messageData) {
        LOG.info("on receive message {}", messageData.getMessageId());
        boolean needResponse = false;
        if (messageData.getConv().getType() == 0) {
            needResponse = true;
        }
        if (!needResponse && messageData.getConv().getType() == 1) {
            if (messageData.getPayload() != null) {
                if (messageData.getPayload().getMentionedType() == 2) {
                    //needResponse = true;
                    needResponse = false; //@全体时，机器人就别乱回复捣乱了
                } else if (messageData.getPayload().getMentionedType() == 1) {
                    if (messageData.getPayload().getMentionedTarget() != null && messageData.getPayload().getMentionedTarget().contains(mRobotConfig.getIm_id())) {
                        needResponse = true;
                    }
                }
            }
        }

        if (!needResponse) {
            // 群聊未被 @ 的消息只记录下来作为后续上下文，不回复
            recordGroupMessage(messageData);
            return;
        }

        {
            boolean replyMessage = messageData.getPayload().getMentionedTarget() != null && messageData.getPayload().getMentionedTarget().contains(mRobotConfig.getIm_id());

            Conversation conversation = messageData.getConv();
            if (conversation.getType() == 0) {
                conversation.setTarget(messageData.getSender());
            }

            String mentionPrefix = null;
            String mentionedUserId = null;
            if (conversation.getType() == 1) {
                mentionedUserId = messageData.getSender();
                mentionPrefix = "@" + displayName(mentionedUserId) + " ";
            }

            String response = messageData.getPayload().getSearchableContent();
            response = response.replace("@" + mRobotConfig.im_name, "").trim();
            // 不打印消息正文，避免把用户聊天内容写进日志
            LOG.info("收到文本消息, messageId:{}, length:{}", messageData.getMessageId(), response.length());
            if (messageData.getPayload().getType() == 1) {
                // 普通文本交给大模型，流式回复用户（会先保存消息并带上最近历史）
                llmReplyService.reply(conversation, messageData.getSender(), response,
                        replyMessage ? messageData.getMessageId() : 0,
                        mentionPrefix, mentionedUserId, messageSender);
                return;
            } else if (messageData.getPayload().getType() == 3) {
                response = "不好意思，我还不会看照片哟～";
            } else if (messageData.getPayload().getType() == 2) {
                response = "不好意思，我还不会听声音哟～";
            } else if (messageData.getPayload().getType() == 4) {
                response = "这是那里？我还没有学会看地图啊！";
            } else if (messageData.getPayload().getType() == 6) {
                response = "我也想看视频，可惜我还没学会！";
            } else if (messageData.getPayload().getType() == 400) {
                response = "别给我打电话了，我是个机器人，还不会接电话，还没有人教过我啊！";
                MessagePayload payload = new MessagePayload();
                payload.setType(402);
                payload.setContent(messageData.getPayload().getContent());
                String reason = "{\"r\":5}";
                payload.setBase64edData(Base64.getEncoder().encodeToString(reason.getBytes(StandardCharsets.UTF_8)));
                try {
                    robotServiceProvider.get().sendMessage(mRobotConfig.getIm_id(), conversation, payload);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if(messageData.getPayload().getType() > 400 && messageData.getPayload().getType() < 500) {
                //voip signal message, ignore it
                return;
            }
            
            MessagePayload payload = new MessagePayload();
            payload.setType(1);
            payload.setSearchableContent(response);
            if (conversation.getType() == 1) { // 群里的回复统一带上 @发送者
                payload.setSearchableContent(mentionPrefix + payload.getSearchableContent());
                payload.setMentionedType(1);
                payload.setMentionedTarget(Arrays.asList(messageData.getSender()));
            }
            try {
                IMResult<SendMessageResult> result;
                if(replyMessage) {
                    result = robotServiceProvider.get().replyMessage(messageData.getMessageId(), payload, false);
                } else {
                    result = robotServiceProvider.get().sendMessage(mRobotConfig.getIm_id(), conversation, payload);
                }
                if (result != null) {
                    if (result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                        LOG.info("Send response success");
                    } else {
                        LOG.error("Send response error {}", result.getCode());
                    }
                } else {
                    LOG.error("Send response is null");
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error("Send response execption");
            }
        }
    }

    /**
     * 群聊里没有 @ 机器人时，只保存用户消息作为后续上下文，不回复。
     */
    private void recordGroupMessage(OutputMessageData messageData) {
        try {
            if (messageData.getConv() == null || messageData.getConv().getType() != 1) {
                return;
            }
            if (messageData.getPayload() == null || messageData.getPayload().getType() != 1) {
                return;
            }
            String sender = messageData.getSender();
            if (sender == null || sender.equals(mRobotConfig.getIm_id())) {
                return;
            }
            String target = messageData.getConv().getTarget();
            String content = messageData.getPayload().getSearchableContent();
            if (target == null || content == null || content.trim().isEmpty()) {
                return;
            }
            chatHistoryService.saveUserMessage(1, target, sender, content.trim());
            LOG.debug("记录群消息, sender:{}, length:{}", sender, content.length());
        } catch (Exception e) {
            LOG.warn("记录群消息失败: {}", e.getMessage());
        }
    }

    /**
     * 获取用户显示名，带 10 分钟缓存；失败时回退为用户ID。
     */
    private String displayName(String userId) {
        long now = System.currentTimeMillis();
        CachedName cached = userNameCache.get(userId);
        if (cached != null && now - cached.cachedAt < USER_NAME_CACHE_TTL_MS) {
            return cached.name;
        }
        String name = userId;
        try {
            IMResult<InputOutputUserInfo> result = robotServiceProvider.get().getUserInfo(userId);
            if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS
                    && result.getResult() != null
                    && result.getResult().getDisplayName() != null
                    && !result.getResult().getDisplayName().trim().isEmpty()) {
                name = result.getResult().getDisplayName();
            }
        } catch (Exception e) {
            LOG.debug("获取用户显示名失败, userId:{}: {}", userId, e.getMessage());
        }
        if (userNameCache.size() > 1000) {
            userNameCache.clear();
        }
        userNameCache.put(userId, new CachedName(name, now));
        return name;
    }

    private static class CachedName {
        private final String name;
        private final long cachedAt;

        private CachedName(String name, long cachedAt) {
            this.name = name;
            this.cachedAt = cachedAt;
        }
    }
}
