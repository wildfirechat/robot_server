package cn.wildfirechat.app;

import cn.wildfirechat.pojos.OutputMessageData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    @Autowired
    private Service mService;

    /**
     * IM 服务器回调入口。该服务只部署在内网，由 IM 服务器调用。
     */
    @PostMapping(value = "/robot/recvmsg", produces = "application/json;charset=UTF-8")
    public Object recvMsg(@RequestBody OutputMessageData messageData) {
        mService.onReceiveMessage(messageData);
        return "ok";
    }
}
