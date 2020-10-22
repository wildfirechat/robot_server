package cn.wildfirechat.app;


import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.sdk.ChatConfig;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.*;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.validation.Payload;
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
        ChatConfig.initRobot(mRobotConfig.im_url, mRobotConfig.getIm_id(), mRobotConfig.im_secret);
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
                    //needResponse = true;
                    needResponse = false; //@全体时，机器人就别乱回复捣乱了
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
//        }
        if (needResponse) {
            Conversation conversation = messageData.getConv();
            if (conversation.getType() == 0) {
                conversation.setTarget(messageData.getSender());
            }

            String response = messageData.getPayload().getSearchableContent();
            boolean localResponse = true;

            response = response.replace("@小火", "").trim();

            if (messageData.getPayload().getType() == 1) {
                if (response.contains("狗屁文章")) {
                    response = response.replace("狗屁文章", "").trim();
                    localResponse = false;
                    response = BullshitGenerator.bullshit(response);
                } else if(response.contains("地址") || response.contains("文档") || response.contains("论坛")) {
                    localResponse = false;
                    response = "项目地址在: https://github.com/wildfirechat。 文档地址在: http://docs.wildfirechat.cn。论坛地址在: http://bbs.wildfirechat.cn";
                } else if(response.startsWith("公众号")) {
                    localResponse = false;
                    response = "请关注微信公众号：野火IM";
                } else if(response.startsWith("价格") || response.startsWith("收费") || response.startsWith("费用")) {
                    localResponse = false;
                    response = "野火IM相关价格，请参考我们的文档: http://docs.wildfirechat.cn";
                } else if(response.startsWith("商务") || response.startsWith("购买") || response.startsWith("联系")) {
                    localResponse = false;
                    response = "请微信联系 wildfirechat 或 wfchat 进行商务交流";
                } else if(response.startsWith("问题") || response.startsWith("崩溃")) {
                    localResponse = false;
                    response = "请确保是按照文档进行对接使用。请检索github issue或者论坛bbs.wildfirechat.cn。如果还无法解决请提issue或者论坛发帖";
                } else if (mRobotConfig.use_tuling) {
                    String searchReq = "{\n" + "\t\"reqType\":0,\n" + "    \"perception\": {\n" + "        \"inputText\": {\n" + "            \"text\": \"${TEXT}\"\n" + "        }\n" + "    },\n" + "    \"userInfo\": {\n" + "        \"apiKey\": \"${APIKEY}\",\n" + "        \"userId\": \"${USERID}\"\n" + "    }\n" + "}";
                    searchReq = searchReq.replace("${APIKEY}", mRobotConfig.getTuling_key()).replace("${USERID}", Math.abs(messageData.getSender().hashCode()) + "");
                    searchReq = searchReq.replace("${TEXT}", response);

                    try {
                        TulingResponse s = HttpUtils.post("http://openapi.tuling123.com/openapi/api/v2", searchReq, TulingResponse.class);
                        if (s != null) {
                            if (s.results != null && s.results.size() > 0) {
                                for (TulingResponse.Result result : s.results
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
            } else if (messageData.getPayload().getType() == 2) {
                response = "不好意思，我还不会看照片哟～";
            } else if (messageData.getPayload().getType() == 3) {
                response = "不好意思，我还不会听声音哟～";
            } else if (messageData.getPayload().getType() == 4) {
                response = "这是那里？我还没有学会看地图啊！";
            } else if (messageData.getPayload().getType() == 5) {
                response = "我也想看视频，可惜我还没学会！";
            } else if (messageData.getPayload().getType() == 400) {
                response = "别给我打电话了，我是个机器人，还不会接电话，还没有人教过我啊！";

                MessagePayload payload = new MessagePayload();
                payload.setType(402);
                payload.setContent(messageData.getPayload().getContent());
                try {
                    RobotService.sendMessage(mRobotConfig.getIm_id(), conversation, payload);
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

            MessagePayload payload = new MessagePayload();
            payload.setType(1);
            payload.setSearchableContent(response);
            if (conversation.getType() == 1 && messageData.getPayload().getType() == 1) { //群里的文本，加上@信息
                InputOutputUserInfo sender = null;
                try {
                    IMResult<InputOutputUserInfo> result = RobotService.getUserInfo(messageData.getSender());
                    if (result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                        sender = result.getResult();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (sender != null && sender.getDisplayName() != null) {
                    payload.setSearchableContent("@" + sender.getDisplayName() + " " + payload.getSearchableContent());
                    payload.setMentionedType(1);
                    payload.setMentionedTarget(Arrays.asList(messageData.getSender()));
                }
            }
            try {
                IMResult<SendMessageResult> result = RobotService.sendMessage(mRobotConfig.getIm_id(), conversation, payload);
                if (result != null) {
                    if (result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
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
