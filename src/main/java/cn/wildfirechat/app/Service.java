package cn.wildfirechat.app;


import cn.wildfirechat.sdk.model.SendMessageData;

public interface Service {
    RestResult onReceiveMessage(SendMessageData messageData);
}
