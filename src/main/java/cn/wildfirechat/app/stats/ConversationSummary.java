package cn.wildfirechat.app.stats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 会话摘要。
 */
public class ConversationSummary {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final int convType;
    private final String convTarget;
    private final long messageCount;
    private final LocalDateTime lastTime;

    public ConversationSummary(int convType, String convTarget, long messageCount, LocalDateTime lastTime) {
        this.convType = convType;
        this.convTarget = convTarget;
        this.messageCount = messageCount;
        this.lastTime = lastTime;
    }

    public int getConvType() {
        return convType;
    }

    public String getConvTypeText() {
        return convType == 0 ? "单聊" : (convType == 1 ? "群聊" : "其他");
    }

    public String getConvTarget() {
        return convTarget;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public LocalDateTime getLastTime() {
        return lastTime;
    }

    public String getLastTimeText() {
        return lastTime == null ? "" : lastTime.format(TIME_FORMAT);
    }
}
