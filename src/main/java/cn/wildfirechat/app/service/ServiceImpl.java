package cn.wildfirechat.app.service;

import cn.wildfirechat.app.jpa.PostRepository;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

@Component
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

   @Value("${im.url}")
   private String imUrl;

    @Value("${robot.secret}")
    private String robotSecret;

    @Value("${robot.id}")
    private String robotId;


    @Autowired
    private PostRepository postRepository;

    private RobotService robotService;

    @PostConstruct
    private void init() {
        robotService = new RobotService(imUrl, robotId, robotSecret);
    }

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
                    if (messageData.getPayload().getMentionedTarget() != null && messageData.getPayload().getMentionedTarget().contains(robotId)) {
                        needResponse = true;
                    }
                }
            }
        }

        if (needResponse) {
            Conversation conversation = messageData.getConv();
            if (conversation.getType() == 0) {
                conversation.setTarget(messageData.getSender());
            }

            MessagePayload payload = new MessagePayload();
            payload.setType(1);
            payload.setSearchableContent("好的，我收到了");
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
                IMResult<SendMessageResult> result = robotService.sendMessage(robotId, conversation, payload);
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
        return;
    }
}
