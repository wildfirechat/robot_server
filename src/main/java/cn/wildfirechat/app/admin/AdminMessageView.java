package cn.wildfirechat.app.admin;

import cn.wildfirechat.app.chat.ChatMessage;

/**
 * 管理后台消息展示行：把内部 ID 转换成用户/群名称。
 */
public class AdminMessageView {
    private final ChatMessage message;
    private final String senderName;
    private final String conversationName;

    public AdminMessageView(ChatMessage message, String senderName, String conversationName) {
        this.message = message;
        this.senderName = senderName;
        this.conversationName = conversationName;
    }

    public long getId() {
        return message.getId();
    }

    public String getSendTimeText() {
        return message.getSendTimeText();
    }

    public int getConvType() {
        return message.getConvType();
    }

    public String getConvTarget() {
        return message.getConvTarget();
    }

    public String getConvTypeText() {
        return message.getConvType() == 0 ? "单聊" : "群聊";
    }

    public String getConversationName() {
        return conversationName;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getRole() {
        return message.getRole();
    }

    public String getContent() {
        return message.getContent();
    }
}
