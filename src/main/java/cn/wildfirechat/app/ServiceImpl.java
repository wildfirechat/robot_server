package cn.wildfirechat.app;

import cn.wildfirechat.app.tuling.TulingService;
import cn.wildfirechat.app.webhook.WebhookService;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.messagecontentbuilder.StreamingTextMessageContentBuilder;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.sdk.MessageAdmin;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import sun.misc.BASE64Encoder;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.UUID;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    @Autowired
    private RobotConfig mRobotConfig;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private TulingService tulingService;

    private RobotService robotService;

    @PostConstruct
    private void init() {
        robotService = new RobotService(mRobotConfig.im_url, mRobotConfig.getIm_id(), mRobotConfig.im_secret);
    }

//    int ConversationType_Private = 0;
//    int ConversationType_Group = 1;
//    int ConversationType_ChatRoom = 2;
//    int ConversationType_Channel = 3;
//    int ConversationType_Thing = 4;
    @Override
    @Async("asyncExecutor")
    public void onReceiveMessage(OutputMessageData messageData) {
        LOG.info("on receive message {}", messageData.getMessageId());
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

            LOG.info("msg:{}, robotName:{}", response, mRobotConfig.im_name);
            response = response.replace("@" + mRobotConfig.im_name, "").trim();
            LOG.info("msg:{}, robotName:{}", response, mRobotConfig.im_name);
            if (messageData.getPayload().getType() == 1) {
                if(response.contains("地址") || response.contains("文档") || response.contains("论坛") || response.contains("官网")) {
                    localResponse = false;
                    response = "项目地址在: https://github.com/wildfirechat。 文档地址在: https://docs.wildfirechat.cn。论坛地址在: https://bbs.wildfirechat.cn。官网地址在：https://wildfirechat.cn";
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
                } else if(response.equalsIgnoreCase("/list") || response.equalsIgnoreCase("/")) {
                    if (conversation.getType() == 1 || conversation.getType() == 0) {
                        response = webhookService.InvokeCommands();
                    } else {
                        response = "仅支持群组和私聊";
                    }
                } else if(response.equals("流式文本")) {
                    testStreamingText(conversation);
                    return;
                } else if(webhookService.handleInvokeCommand(response, messageData.getSender(), messageData.getConv())) {
                    return;
                } else {
                    response = tulingService.handleWord(messageData.getSender(), response);
                }
            } else if (messageData.getPayload().getType() == 3) {
                localResponse = false;
                response = "不好意思，我还不会看照片哟～";
            } else if (messageData.getPayload().getType() == 2) {
                localResponse = false;
                response = "不好意思，我还不会听声音哟～";
            } else if (messageData.getPayload().getType() == 4) {
                localResponse = false;
                response = "这是那里？我还没有学会看地图啊！";
            } else if (messageData.getPayload().getType() == 6) {
                localResponse = false;
                response = "我也想看视频，可惜我还没学会！";
            } else if (messageData.getPayload().getType() == 400) {
                localResponse = false;
                response = "别给我打电话了，我是个机器人，还不会接电话，还没有人教过我啊！";

                MessagePayload payload = new MessagePayload();
                payload.setType(402);
                payload.setContent(messageData.getPayload().getContent());
                String reason = "{\"r\":5}";
                payload.setBase64edData(new BASE64Encoder().encode(reason.getBytes()));
                try {
                    robotService.sendMessage(mRobotConfig.getIm_id(), conversation, payload);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if(messageData.getPayload().getType() > 400 && messageData.getPayload().getType() < 500) {
                //voip signal message, ignore it
                return;
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
                    IMResult<InputOutputUserInfo> result = robotService.getUserInfo(messageData.getSender());
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
                IMResult<SendMessageResult> result = robotService.sendMessage(mRobotConfig.getIm_id(), conversation, payload);
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
        return;
    }


    void testStreamingText(Conversation conversation) {
        String fullText = "北京野火无限网络科技有限公司是成立于2019年底的一家科技创新企业，公司的主要目标是为广大企业和单位提供优质可控、私有部署的即时通讯和实时音视频能力，为社会信息化水平提高作出自己的贡献。\n" +
                "\n" +
                "野火IM是公司研发一套自主可控的即时通讯组件，具有全部私有化、功能齐全、协议稳定可靠、全平台支持、安全性高和支持国产化等技术特点。客户端分层设计，既可开箱即用，也可与现有系统深度融合。具有完善的服务端API和自定义消息功能，可以任意扩展功能。代码开源率高，方便二次开发和使用。支持多人实时音视频和会议功能，线上沟通更通畅。\n" +
                "\n" +
                "公司致力于开源项目，在Github上开源项目广受好评，其中Server项目有超过7.1K个Star，组织合计Star超过1万个。有大量的技术公司受益于我们的开源，为自己的产品添加了即时通讯能力，这也算是我们公司为社会信息化建设做出的一点点贡献吧。\n" +
                "\n" +
                "公司以即时通讯技术为核心，持续努力优化和完善即时通讯和实时音视频产品，努力为客户提供最优质的即时通讯和实时音视频能力。";
        int i = 0;
        String streamId = UUID.randomUUID().toString();
        while (i < fullText.length()) {
            i+= 15;

            boolean finish = i >= fullText.length();
            String partText = finish?fullText:fullText.substring(0, i);

            MessagePayload payload = StreamingTextMessageContentBuilder.newBuilder(streamId).text(partText).generating(!finish).build();

            try {
                IMResult<SendMessageResult> resultSendMessage = robotService.sendMessage(mRobotConfig.getIm_id(), conversation, payload);
                if (resultSendMessage != null && resultSendMessage.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    System.out.println("send message success");
                } else {
                    System.out.println("send message failure");
                    return;
                }

                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
