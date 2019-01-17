package cn.wildfirechat.sdk.model;


public class SendMessageData {
    private String sender;
    private Conversation conv;
    private MessagePayload payload;

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public Conversation getConv() {
        return conv;
    }

    public void setConv(Conversation conv) {
        this.conv = conv;
    }

    public MessagePayload getPayload() {
        return payload;
    }

    public void setPayload(MessagePayload payload) {
        this.payload = payload;
    }

    public static boolean isValide(SendMessageData sendMessageData) {
        if(sendMessageData == null ||
            sendMessageData.getConv() == null ||
            sendMessageData.getConv().getType() < 0 ||
            sendMessageData.getConv().getType() > 6 ||
            sendMessageData.getConv().getTarget() != null ||
            sendMessageData.getSender() != null ||
            sendMessageData.getPayload() == null) {
            return false;
        }
        return true;
    }

}
