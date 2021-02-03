package cn.wildfirechat.app;

import cn.wildfirechat.pojos.Conversation;

public class TokenUtils {
    private static final String signWord = "hello world";
    public static String webhookToken(Conversation conversation, String user) {
        String key = conversation.getType() + "|" + conversation.getLine() + "|" + conversation.getTarget()  + signWord + user;
        return DESUtil.encrypt(key);
    }

    public static String userFromToken(String token) {
        String s = DESUtil.decrypt(token);
        if(s == null) {
            return null;
        }

        return s.substring(s.indexOf(signWord) + signWord.length());
    }
    public static Conversation conversationFromToken(String token) {
        String s = DESUtil.decrypt(token);
        if(s == null) {
            return null;
        }
        Conversation conversation = new Conversation();
        String[] arr = s.split("\\|");
        conversation.setType(Integer.parseInt(arr[0]));
        conversation.setLine(Integer.parseInt(arr[1]));
        String target = s.substring((conversation.getType()+"|"+conversation.getLine()+"|").length(), s.indexOf(signWord));
        conversation.setTarget(target);
        return conversation;
    }
}
