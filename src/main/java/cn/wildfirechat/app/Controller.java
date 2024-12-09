package cn.wildfirechat.app;

import cn.wildfirechat.app.webhook.WebhookService;
import cn.wildfirechat.pojos.OutputMessageData;
import cn.wildfirechat.pojos.SendMessageData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
public class Controller {
    @Autowired
    private Service mService;

    @Autowired
    private WebhookService webhookService;

    @PostMapping(value = "/robot/recvmsg", produces = "application/json;charset=UTF-8"   )
    public Object recvMsg(@RequestBody OutputMessageData messageData) {
        mService.onReceiveMessage(messageData);
        return "ok";
    }

    @PostMapping(value = "/robot/recvmsg/conference", produces = "application/json;charset=UTF-8"   )
    public Object recvConferenceEvent(@RequestBody String event) {
        mService.onReceiveConferenceEvent(event);
        return "ok";
    }

    @PostMapping(value = "/robot/webhook/{app}/{token}", produces = "application/json;charset=UTF-8"   )
    public Object webhook(HttpServletRequest request, @PathVariable("app") String app, @PathVariable("token") String token, @RequestBody String payload) {
        return webhookService.handleWebhookPost(request, "/"+app, token, payload);
    }
}
