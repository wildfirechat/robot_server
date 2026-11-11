package cn.wildfirechat.app.chat;

/**
 * 会话中的一条聊天记录，用于持久化用户与机器人的对话内容。
 */
public class ChatMessage {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private long id;
    private int convType;
    private String convTarget;
    private String sender;
    private String role;
    private String content;
    private long sendTime;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getConvType() {
        return convType;
    }

    public void setConvType(int convType) {
        this.convType = convType;
    }

    public String getConvTarget() {
        return convTarget;
    }

    public void setConvTarget(String convTarget) {
        this.convTarget = convTarget;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }
}
