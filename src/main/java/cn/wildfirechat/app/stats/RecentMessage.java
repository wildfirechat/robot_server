package cn.wildfirechat.app.stats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 最近消息展示项。
 */
public class RecentMessage {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final int convType;
    private final String convTarget;
    private final String role;
    private final String preview;
    private final LocalDateTime time;

    public RecentMessage(int convType, String convTarget, String role, String preview, LocalDateTime time) {
        this.convType = convType;
        this.convTarget = convTarget;
        this.role = role;
        this.preview = preview;
        this.time = time;
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

    public String getRole() {
        return role;
    }

    public String getRoleText() {
        return "assistant".equals(role) ? "机器人" : "用户";
    }

    public String getPreview() {
        return preview;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public String getTimeText() {
        return time == null ? "" : time.format(TIME_FORMAT);
    }
}
