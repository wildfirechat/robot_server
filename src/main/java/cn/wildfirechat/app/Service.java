package cn.wildfirechat.app;


import cn.wildfirechat.pojos.SendMessageData;

public interface Service {
    Object onReceiveMessage(SendMessageData messageData);
}
