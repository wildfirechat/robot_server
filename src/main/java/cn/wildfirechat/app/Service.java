package cn.wildfirechat.app;


import cn.wildfirechat.pojos.SendMessageData;

public interface Service {
    RestResult onReceiveMessage(SendMessageData messageData);
}
