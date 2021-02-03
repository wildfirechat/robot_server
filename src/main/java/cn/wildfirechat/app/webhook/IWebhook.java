package cn.wildfirechat.app.webhook;

import javax.servlet.http.HttpServletRequest;

public interface IWebhook {
    interface SendMessageCallback {
        void sendMessage(String text);
    }

    String invokeCommand();
    Object handleWebhookPost(HttpServletRequest request, String payload, SendMessageCallback callback);
}
