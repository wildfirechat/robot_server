package cn.wildfirechat.app.service;


import cn.wildfirechat.pojos.OutputMessageData;
import cn.wildfirechat.pojos.SendMessageData;

public interface Service {
    void onReceiveMessage(OutputMessageData messageData);
}
