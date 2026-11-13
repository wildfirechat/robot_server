package cn.wildfirechat.app;


import cn.wildfirechat.pojos.OutputMessageData;

public interface Service {
    void onReceiveMessage(OutputMessageData messageData);
}
