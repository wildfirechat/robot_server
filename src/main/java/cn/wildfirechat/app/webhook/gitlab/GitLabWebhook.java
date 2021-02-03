package cn.wildfirechat.app.webhook.gitlab;

import cn.wildfirechat.app.webhook.IWebhook;
import cn.wildfirechat.pojos.Conversation;

import javax.servlet.http.HttpServletRequest;

public class GitLabWebhook implements IWebhook {
    @Override
    public String invokeCommand() {
        return "/gitlab";
    }

    @Override
    public Object handleWebhookPost(HttpServletRequest request, String payload, SendMessageCallback callback) {
        return null;
    }
}
