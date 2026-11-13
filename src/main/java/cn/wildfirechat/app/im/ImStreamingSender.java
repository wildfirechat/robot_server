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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 把大模型的流式增量转换成野火IM的流式文本消息。
 * 发送操作在独立的单线程中串行执行，避免阻塞大模型 SSE 读取；
 * 两次推送之间至少间隔 streamIntervalMs 毫秒，且最多允许两个发送任务积压。
 */
public class ImStreamingSender implements OpenAiLlmClient.StreamListener {
    private static final Logger LOG = LoggerFactory.getLogger(ImStreamingSender.class);
    private static final long DEFAULT_INTERVAL_MS = 500;
    private static final int MAX_PENDING_SENDS = 2;
    private static final long SEND_TIMEOUT_SECONDS = 30;

    private final MessageSender messageSender;
    private final Conversation conversation;
    private final long replyMessageId;
    private final long intervalMs;
    private final String streamId = UUID.randomUUID().toString();
    private final StringBuilder fullText = new StringBuilder();
    private final AtomicInteger pendingSends = new AtomicInteger();
    private final AtomicReference<Throwable> sendError = new AtomicReference<>();

    private ExecutorService sendExecutor;
    private long lastSentAt;
    private int sentCount;
    private boolean finished;

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
        if ((sentCount == 0 || now - lastSentAt >= intervalMs)
                && pendingSends.get() < MAX_PENDING_SENDS) {
            enqueue(false);
            lastSentAt = now;
            sentCount++;
        }
    }

    /**
     * 结束流式回复：排队发送最终消息，等待所有发送任务完成。
     */
    public synchronized void finish() throws Exception {
        if (finished) {
            throwIfSendFailed();
            return;
        }
        finished = true;
        if (fullText.length() == 0) {
            shutdownExecutor();
            return;
        }
        enqueue(true);
        shutdownExecutor();
        throwIfSendFailed();
    }

    private void enqueue(boolean lastChunk) {
        String text = fullText.toString();
        if (text.isEmpty()) {
            return;
        }
        pendingSends.incrementAndGet();
        try {
            executor().execute(() -> {
                try {
                    send(text, lastChunk);
                } catch (Throwable t) {
                    sendError.compareAndSet(null, t);
                } finally {
                    pendingSends.decrementAndGet();
                }
            });
        } catch (RuntimeException e) {
            pendingSends.decrementAndGet();
            sendError.compareAndSet(null, e);
        }
    }

    private synchronized ExecutorService executor() {
        if (sendExecutor == null) {
            String threadName = "im-stream-" + streamId.substring(0, 8);
            sendExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(true);
                return thread;
            });
        }
        return sendExecutor;
    }

    private void shutdownExecutor() throws Exception {
        ExecutorService executor = sendExecutor;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                sendError.compareAndSet(null, new IllegalStateException("发送流式消息超时"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            sendError.compareAndSet(null, e);
        }
    }

    private void throwIfSendFailed() throws Exception {
        Throwable error = sendError.get();
        if (error == null) {
            return;
        }
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        throw new IllegalStateException(error);
    }

    private void send(String text, boolean lastChunk) throws Exception {
        MessageContent content = lastChunk
                ? new StreamTextGeneratedMessageContent(text, streamId)
                : new StreamTextGeneratingMessageContent(text, streamId);
        MessagePayload payload = content.encode();
        messageSender.send(conversation, replyMessageId, payload);
        LOG.debug("推送流式文本, finished:{}, length:{}, streamId:{}", lastChunk, text.length(), streamId);
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
