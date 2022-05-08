package cn.wildfirechat.app.webhook.general;

import cn.wildfirechat.app.webhook.IWebhook;
import cn.wildfirechat.pojos.MessagePayload;
import org.apache.commons.codec.binary.Base64;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

public class GeneralWebhook implements IWebhook {
    @Override
    public String invokeCommand() {
        return "/general";
    }

    @Override
    public Object handleWebhookPost(HttpServletRequest request, String user, String body, SendMessageCallback callback) {
        MessagePayload messagePayload = new MessagePayload();
        try {
            JSONObject object = (JSONObject)(new JSONParser().parse(body));
            long type = (Long) object.get("type");
            if(type == 1) {
                String text = (String)object.get("text");
                messagePayload.setType(1);
                messagePayload.setSearchableContent(text + "\n\n来自 " + user + " 的机器人消息");
            } else if(type == 8) {
                String title = (String) object.get("title");
                String digest = (String) object.get("digest");
                String url = (String) object.get("url");
                messagePayload.setType(8);
                messagePayload.setSearchableContent(title);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("d", digest);
                jsonObject.put("u", url);
                messagePayload.setBase64edData(new Base64().encodeAsString(jsonObject.toString().getBytes(StandardCharsets.UTF_8)));
            } else {
                messagePayload.setType(1);
                messagePayload.setSearchableContent(body + "\n\n来自 " + user + " 的机器人消息");
            }
        } catch (Exception e) {
            e.printStackTrace();
            messagePayload.setType(1);
            messagePayload.setSearchableContent(body + "\n\n来自 " + user + " 的机器人消息");
        }

        callback.sendMessage(messagePayload);
        return "ok";
    }
}
