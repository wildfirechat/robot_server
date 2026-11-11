package cn.wildfirechat.app.im;

import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.pojos.SendMessageResult;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.IMResult;

/**
 * 基于野火机器人 SDK 的消息发送实现。
 */
public class RobotMessageSender implements MessageSender {
    private final RobotService robotService;
    private final String robotId;

    public RobotMessageSender(RobotService robotService, String robotId) {
        this.robotService = robotService;
        this.robotId = robotId;
    }

    @Override
    public void send(Conversation conversation, long replyMessageId, MessagePayload payload) throws Exception {
        IMResult<SendMessageResult> result;
        if (replyMessageId > 0) {
            result = robotService.replyMessage(replyMessageId, payload, false);
        } else {
            result = robotService.sendMessage(robotId, conversation, payload);
        }
        if (result == null) {
            throw new IllegalStateException("发送消息失败: 接口返回空");
        }
        if (result.getErrorCode() != ErrorCode.ERROR_CODE_SUCCESS) {
            throw new IllegalStateException("发送消息失败: code=" + result.getCode());
        }
    }
}
