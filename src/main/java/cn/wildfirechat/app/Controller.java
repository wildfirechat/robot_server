package cn.wildfirechat.app;

import cn.wildfirechat.app.service.Service;
import cn.wildfirechat.pojos.OutputMessageData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class Controller {
    @Autowired
    private Service mService;

    @PostMapping(value = "/robot/recvmsg", produces = "application/json;charset=UTF-8"   )
    public Object recvMsg(@RequestBody OutputMessageData messageData) {
        mService.onReceiveMessage(messageData);
        return "ok";
    }
}
