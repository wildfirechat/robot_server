package cn.wildfirechat.app.webhook;

import cn.wildfirechat.pojos.MessagePayload;

import javax.servlet.http.HttpServletRequest;

public interface IWebhook {
    interface SendMessageCallback {
        void sendMessage(MessagePayload payload);
    }

    String invokeCommand();
    Object handleWebhookPost(HttpServletRequest request, String user, String body, SendMessageCallback callback);
}
