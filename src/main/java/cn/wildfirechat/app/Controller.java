package cn.wildfirechat.app;

import cn.wildfirechat.app.call.CallService;
import cn.wildfirechat.app.webhook.WebhookService;
import cn.wildfirechat.pojos.OutputMessageData;
import cn.wildfirechat.pojos.SendMessageData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class Controller {
    @Autowired
    private Service mService;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private CallService callService;

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

    /**
     * Returns active HLS stream URL paths keyed by callId.
     * Example response: {"call-abc": "/hls/call-abc/stream.m3u8"}
     * Prepend the server base URL to construct a playable playlist link.
     */
    @GetMapping(value = "/hls/streams", produces = "application/json;charset=UTF-8")
    public Map<String, String> getActiveHlsStreams() {
        return callService.getActiveHlsStreams();
    }
}
