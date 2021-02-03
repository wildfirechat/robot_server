package cn.wildfirechat.app.webhook.general;

import cn.wildfirechat.app.webhook.IWebhook;

import javax.servlet.http.HttpServletRequest;

public class GeneralWebhook implements IWebhook {
    @Override
    public String invokeCommand() {
        return "/general";
    }

    @Override
    public Object handleWebhookPost(HttpServletRequest request, String payload, SendMessageCallback callback) {
        callback.sendMessage(payload);
        return "ok";
    }
}
