package cn.wildfirechat.app.im;

import cn.wildfirechat.app.llm.OpenAiLlmClient;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.sdk.messagecontent.MessageContent;
import cn.wildfirechat.sdk.messagecontent.StreamTextGeneratedMessageContent;
import cn.wildfirechat.sdk.messagecontent.StreamTextGeneratingMessageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 把大模型的流式增量转换成野火IM的流式文本消息。
 * 为减少消息条数，两次推送之间至少间隔 streamIntervalMs 毫秒；结束时发送一条 Generated 消息。
 */
public class ImStreamingSender implements OpenAiLlmClient.StreamListener {
    private static final Logger LOG = LoggerFactory.getLogger(ImStreamingSender.class);
    private static final long DEFAULT_INTERVAL_MS = 500;

    private final MessageSender messageSender;
    private final Conversation conversation;
    private final long replyMessageId;
    private final long intervalMs;
    private final String streamId = UUID.randomUUID().toString();
    private final StringBuilder fullText = new StringBuilder();

    private long lastSentAt;
    private int sentCount;

    public ImStreamingSender(MessageSender messageSender, Conversation conversation,
                             long replyMessageId, long intervalMs) {
        this.messageSender = messageSender;
        this.conversation = conversation;
        this.replyMessageId = replyMessageId;
        this.intervalMs = intervalMs > 0 ? intervalMs : DEFAULT_INTERVAL_MS;
    }

    @Override
    public void onDelta(String delta) throws Exception {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        fullText.append(delta);
        long now = System.currentTimeMillis();
        if (sentCount == 0 || now - lastSentAt >= intervalMs) {
            send(false);
        }
    }

    /**
     * 结束流式回复：发送最终消息，通知客户端流式内容已生成完毕。
     */
    public void finish() throws Exception {
        if (fullText.length() == 0) {
            return;
        }
        send(true);
    }

    private void send(boolean finished) throws Exception {
        String text = fullText.toString();
        if (text.isEmpty()) {
            return;
        }
        MessageContent content = finished
                ? new StreamTextGeneratedMessageContent(text, streamId)
                : new StreamTextGeneratingMessageContent(text, streamId);
        MessagePayload payload = content.encode();
        messageSender.send(conversation, replyMessageId, payload);
        lastSentAt = System.currentTimeMillis();
        sentCount++;
        LOG.debug("推送流式文本, finished:{}, length:{}, streamId:{}", finished, text.length(), streamId);
    }

    public String getFullText() {
        return fullText.toString();
    }

    public boolean hasSent() {
        return sentCount > 0;
    }

    public String getStreamId() {
        return streamId;
    }
}
