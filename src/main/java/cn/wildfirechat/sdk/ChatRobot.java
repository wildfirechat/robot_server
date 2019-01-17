package cn.wildfirechat.sdk;

import cn.wildfirechat.sdk.model.*;

public class ChatRobot {
    public static void init(String robotId, String url, String secret) {
        HttpUtils.init(robotId, url, secret);
    }

    public static IMResult<User> getUserById(String userId) throws Exception {
        String path = "/admin/robot/user_info";
        GetUserRequest request = new GetUserRequest();
        request.setUserId(userId);
        return HttpUtils.IMPost(path, request, User.class);
    }

    public static IMResult<SendMessageResult> sendMessage(SendMessageData data) throws Exception {
        String path = "/admin/robot/send";
        return HttpUtils.IMPost(path, data, SendMessageResult.class);
    }
}
