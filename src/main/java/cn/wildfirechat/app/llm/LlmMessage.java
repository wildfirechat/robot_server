package cn.wildfirechat.app.llm;

/**
 * 发送给 OpenAI 兼容接口的一条消息。
 */
public class LlmMessage {
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private final String role;
    private final String content;

    public LlmMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return role + ": " + content;
    }
}
