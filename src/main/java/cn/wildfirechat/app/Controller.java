package cn.wildfirechat.app;

import cn.wildfirechat.pojos.OutputMessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    private static final Logger LOG = LoggerFactory.getLogger(Controller.class);

    @Autowired
    private Service mService;

    /**
     * IM 服务器回调入口。该服务只部署在内网，由 IM 服务器调用。
     * 队列满时返回 503，由 IM/上游做背压，避免任务无限堆积。
     */
    @PostMapping(value = "/robot/recvmsg", produces = "application/json;charset=UTF-8")
    public Object recvMsg(@RequestBody OutputMessageData messageData) {
        try {
            mService.onReceiveMessage(messageData);
            return "ok";
        } catch (TaskRejectedException e) {
            LOG.warn("机器人任务队列已满，拒绝消息 {}", messageData.getMessageId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("busy");
        }
    }
}
