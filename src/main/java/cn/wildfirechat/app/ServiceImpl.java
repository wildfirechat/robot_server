package cn.wildfirechat.app;


import cn.wildfirechat.sdk.ChatRobot;
import cn.wildfirechat.sdk.HttpUtils;
import cn.wildfirechat.sdk.model.*;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);
    private static ConcurrentHashMap<String, Record> mRecords = new ConcurrentHashMap<>();

    @Autowired
    private RobotConfig mRobotConfig;

    @PostConstruct
    private void init() {
        ChatRobot.init(mRobotConfig.getIm_id(), mRobotConfig.im_url, mRobotConfig.im_secret);
    }

//    int ConversationType_Private = 0;
//    int ConversationType_Group = 1;
//    int ConversationType_ChatRoom = 2;
//    int ConversationType_Channel = 3;
//    int ConversationType_Thing = 4;
    @Override
    public RestResult onReceiveMessage(SendMessageData messageData) {
        LOG.info("on receive message {}", new Gson().toJson(messageData));
        boolean needResponse = false;
        if (messageData.getConv().getType() == 0) {
            needResponse = true;
        }
        if (!needResponse && messageData.getConv().getType() == 1) {
            if (messageData.getPayload() != null) {
                if (messageData.getPayload().getMentionedType() == 2) {
                    needResponse = true;
                } else if (messageData.getPayload().getMentionedType() == 1) {
                    if (messageData.getPayload().getMentionedTarget() != null && messageData.getPayload().getMentionedTarget().contains(mRobotConfig.getIm_id())) {
                        needResponse = true;
                    }
                }
            }
        }
//        public interface ContentType {
//            int Unknown = 0;
//            int Text = 1;
//            int Image = 2;
//            int Voice = 3;
//            int Location = 4;
//            int Video = 5;
//            int RichMedia = 6;
//            int Custom = 7;
//        }
        if (needResponse) {

            String response = messageData.getPayload().getSearchableContent();
            boolean localResponse = true;

            if (mRobotConfig.use_tuling && messageData.getPayload().getType() == 1) {
                String searchReq = "{\n" + "\t\"reqType\":0,\n" + "    \"perception\": {\n" + "        \"inputText\": {\n" + "            \"text\": \"${TEXT}\"\n" + "        }\n" + "    },\n" + "    \"userInfo\": {\n" + "        \"apiKey\": \"${APIKEY}\",\n" + "        \"userId\": \"${USERID}\"\n" + "    }\n" + "}";
                searchReq = searchReq.replace("${APIKEY}", mRobotConfig.getTuling_key()).replace("${USERID}", Math.abs(messageData.getSender().hashCode()) + "");
                searchReq = searchReq.replace("${TEXT}", response);

                try {
                    TulingResponse s = HttpUtils.post("http://openapi.tuling123.com/openapi/api/v2", searchReq, TulingResponse.class);
                    if (s != null) {
                        if(s.results != null && s.results.size() > 0) {
                            for (TulingResponse.Result result:s.results
                                 ) {
                                if (result.values != null) {
                                    if (!StringUtils.isEmpty(result.values.text)) {
                                        if (localResponse) {
                                            localResponse = false;
                                            response = result.values.text;
                                        } else {
                                            response = response + " \n" + result.values.text;
                                        }
                                    }

                                    if (!StringUtils.isEmpty(result.values.url)) {
                                        if (localResponse) {
                                            localResponse = false;
                                            response = result.values.url;
                                        } else {
                                            response = response + " \n" + result.values.url;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(localResponse) {
                if (messageData.getPayload().getType() != 1) {
                    response = "我不明白你在说什么";
                } else {
                    response = response.replace("@" + mRobotConfig.getIm_name() + " ", "");
                    response = response.replace("@" + mRobotConfig.getIm_name(), "");
                    response = response.replace("我", "");
                    response = response.replace("吗", "");
                    response = response.replace("你是谁", "我是" + mRobotConfig.getIm_name() + "呀");
                    response = response.replace("你", "我");
                    response = response.replace("?", "!");
                    response = response.replace("? ", "!");
                    response = response.replace("？", "!");
                    response = response.replace("？ ", "!");
                    if (response.equals(messageData.getPayload().getSearchableContent())) {
                        response = "我不明白你在说什么";
                    }
                }
            }
            SendMessageData responseData = new SendMessageData();
            responseData.setConv(messageData.getConv());
            if (messageData.getConv().getType() == 0) {
                messageData.getConv().setTarget(messageData.getSender());
            }
            responseData.setSender(mRobotConfig.getIm_id());
            responseData.setPayload(new MessagePayload());
            responseData.getPayload().setType(1);
            responseData.getPayload().setSearchableContent(response);
            if (messageData.getConv().getType() == 1 && messageData.getPayload().getType() == 1) { //群里的文本，加上@信息
                User sender = null;
                try {
                    IMResult<User> result = ChatRobot.getUserById(messageData.getSender());
                    if (result.getCode() == IMResult.IMResultCode.IMRESULT_CODE_SUCCESS.code) {
                        sender = result.getResult();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (sender != null && sender.getDisplayName() != null) {
                    responseData.getPayload().setSearchableContent("@" + sender.getDisplayName() + "" + responseData.getPayload().getSearchableContent());
                    responseData.getPayload().setMentionedType(1);
                    responseData.getPayload().setMentionedTarget(Arrays.asList(messageData.getSender()));
                }
            }
            try {
                IMResult<SendMessageResult> result = ChatRobot.sendMessage(responseData);
                if (result != null) {
                    if (result.getCode() == IMResult.IMResultCode.IMRESULT_CODE_SUCCESS.code) {
                        LOG.info("Send response success");
                    } else {
                        LOG.error("Send response error {}", result.getCode());
                    }
                } else {
                    LOG.error("Send response is null");
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error("Send response execption");
            }
        }
        return RestResult.ok();
    }
}
