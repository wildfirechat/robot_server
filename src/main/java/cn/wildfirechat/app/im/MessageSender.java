package cn.wildfirechat.app.im;

import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;

/**
 * 消息发送抽象，方便脱离 IM 服务进行单元测试。
 */
public interface MessageSender {
    /**
     * 发送消息。replyMessageId 大于 0 时以回复某条消息的方式发送，否则直接发送到会话。
     */
    void send(Conversation conversation, long replyMessageId, MessagePayload payload) throws Exception;
}
