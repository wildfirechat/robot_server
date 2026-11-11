package cn.wildfirechat.app;

import cn.wildfirechat.app.im.MessageSender;
import cn.wildfirechat.app.im.RobotMessageSender;
import cn.wildfirechat.app.llm.LlmReplyService;
import cn.wildfirechat.app.webhook.WebhookService;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Base64;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    @Autowired
    private RobotConfig mRobotConfig;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private LlmReplyService llmReplyService;

    private RobotService robotService;

    private MessageSender messageSender;

    @PostConstruct
    private void init() {
        robotService = new RobotService(mRobotConfig.im_url, mRobotConfig.getIm_id(), mRobotConfig.im_secret);
        messageSender = new RobotMessageSender(robotService, mRobotConfig.getIm_id());
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

        if (needResponse) {
            boolean replyMessage = messageData.getPayload().getMentionedTarget() != null && messageData.getPayload().getMentionedTarget().contains(mRobotConfig.getIm_id());

            Conversation conversation = messageData.getConv();
            if (conversation.getType() == 0) {
                conversation.setTarget(messageData.getSender());
            }

            String response = messageData.getPayload().getSearchableContent();
            LOG.info("msg:{}, robotName:{}", response, mRobotConfig.im_name);
            response = response.replace("@" + mRobotConfig.im_name, "").trim();
            LOG.info("msg:{}, robotName:{}", response, mRobotConfig.im_name);
            if (messageData.getPayload().getType() == 1) {
                if (response.equalsIgnoreCase("/list") || response.equalsIgnoreCase("/")) {
                    if (conversation.getType() == 1 || conversation.getType() == 0) {
                        response = webhookService.InvokeCommands();
                    } else {
                        response = "仅支持群组和私聊";
                    }
                } else if (webhookService.handleInvokeCommand(response, messageData.getSender(), messageData.getConv())) {
                    return;
                } else {
                    // 普通文本交给大模型，流式回复用户（会先保存消息并带上最近历史）
                    llmReplyService.reply(conversation, messageData.getSender(), response,
                            replyMessage ? messageData.getMessageId() : 0, messageSender);
                    return;
                }
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
                payload.setBase64edData(Base64.getEncoder().encodeToString(reason.getBytes()));
                try {
                    robotService.sendMessage(mRobotConfig.getIm_id(), conversation, payload);
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
            if (conversation.getType() == 1 && messageData.getPayload().getType() == 1) { //群里的文本，加上@信息
                InputOutputUserInfo sender = null;
                try {
                    IMResult<InputOutputUserInfo> result = robotService.getUserInfo(messageData.getSender());
                    if (result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                        sender = result.getResult();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (sender != null && sender.getDisplayName() != null) {
                    payload.setSearchableContent("@" + sender.getDisplayName() + " " + payload.getSearchableContent());
                    payload.setMentionedType(1);
                    payload.setMentionedTarget(Arrays.asList(messageData.getSender()));
                }
            }
            try {
                IMResult<SendMessageResult> result;
                if(replyMessage) {
                    result = robotService.replyMessage(messageData.getMessageId(), payload, false);
                } else {
                    result = robotService.sendMessage(mRobotConfig.getIm_id(), conversation, payload);
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
}
