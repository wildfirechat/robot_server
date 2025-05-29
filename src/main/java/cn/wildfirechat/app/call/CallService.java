package cn.wildfirechat.app.call;

import cn.wildfirechat.*;
import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.OutputMessageData;
import cn.wildfirechat.sdk.RobotService;
import dev.onvoid.webrtc.media.video.VideoTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Service
public class CallService {
    private static final Logger LOG = LoggerFactory.getLogger(CallService.class);
    @Autowired
    private RobotConfig mRobotConfig;

    @Value("${ice.url}")
    private String iceUrl;

    @Value("${ice.password}")
    private String icePassword;

    @Value("${ice.username}")
    private String iceUsername;

    @Value("${video.file.path}")
    private String videoFilePath;

    private Map<String, ImageVideoSink> imageVideoSinkMap = new HashMap<>();

    private final Map<String, Boolean> engineTypeMap = new HashMap<>();

    @PostConstruct
    private void init() {
        RobotService robotService = new RobotService(mRobotConfig.im_url, mRobotConfig.getIm_id(), mRobotConfig.im_secret);

        //1. 初始化音视频SDK
        AVEngineKit.getInstance().init(robotService, new AVEngineKitCallback() {
            @Override
            public void onReceiveCall(CallSession callSession) {
                LOG.info("onReceiveCall: {}", callSession.getCallId());
                for (String participant : callSession.getParticipants()) {
                    engineTypeMap.put(participant, callSession.isAdvanceEngine());
                }
                LOG.info("engineTypeMap: {}, {}", engineTypeMap.size(), engineTypeMap);

                callSession.setEventCallback(new CallEventCallback() {
                    @Override
                    public void onCallStateUpdated(CallSession callSession, CallState state) {

                    }

                    @Override
                    public void onParticipantJoined(CallSession callSession, String userId) {

                    }

                    @Override
                    public void onParticipantConnected(CallSession callSession, String userId) {

                    }

                    @Override
                    public void onReceiveRemoteVideoTrack(CallSession callSession, String userId, VideoTrack videoTrack) {
                        String key = userId + "_" + callSession.getCallId();
                        if(!imageVideoSinkMap.containsKey(key)) {
                            ImageVideoSink imageVideoSink = new ImageVideoSink(userId, callSession.getCallId());
                            imageVideoSinkMap.put(key, imageVideoSink);
                            videoTrack.addSink(imageVideoSink);
                        }
                    }

                    @Override
                    public void onParticipantLeft(CallSession callSession, String userId, CallEndReason reason) {

                    }

                    @Override
                    public void onCallEnd(CallSession callSession, CallEndReason endReason) {
                        for (ImageVideoSink value : imageVideoSinkMap.values()) {
                            if(value.callId.equals(callSession.getCallId())) {
                                value.onCallEnded();
                            }
                        }
                    }
                });

                // for rtsp
//                RtspCapturer capturer =  RtspCapturer.createAndProbe("rtsp://192.168.2.186:8554/live/stream1");
//                callSession.setAudioDevice(capturer);
//                if(!callSession.isAudioOnly()) {
//                    callSession.setVideoCapture(capturer);
//                }

                callSession.setAudioDevice(new EchoAudioDevice(callSession.getConversation()));
                if(!callSession.isAudioOnly()) {
                    callSession.setVideoCapture(new FileVideoCapture(videoFilePath, callSession.getConversation(), callSession.getCallId()));
                }

                //延迟3秒接听
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        callSession.answer(callSession.isAudioOnly());
                    }
                }).start();
            }
        });


        //2. 设置turn服务地址，如果有多个，可以嗲用多次。如果是高级版，可以不用设置turn服务.
        if(!StringUtils.isEmpty(iceUrl)) {
            //如果是高级版，不用设置turn服务。
            AVEngineKit.getInstance().addIceServer(iceUrl, iceUsername, icePassword);
        }

        //3. 打开webrtc的日志，一般不用打开，除非出现问题需要debug
        AVEngineKit.getInstance().enableWebRTCLog();
    }

    public boolean hasPreferEngine(String userId) {
        boolean hasPreferEngine = engineTypeMap.containsKey(userId);
        if(!hasPreferEngine) {
            LOG.info("hasPreferEngine false, {}, {}", userId, engineTypeMap);
        }
        return hasPreferEngine;
    }

    public boolean isAdvanceEngine(String userId) {
        return engineTypeMap.get(userId);
    }

    public void startPrivateCall(Conversation conversation, boolean audioOnly, boolean advanceEngine) {
        CallSession callSession = AVEngineKit.getInstance().startPrivateCall(conversation, audioOnly, advanceEngine, new EchoAudioDevice(conversation), new CallEventCallback() {
            @Override
            public void onCallStateUpdated(CallSession callSession, CallState state) {

            }

            @Override
            public void onParticipantJoined(CallSession callSession, String userId) {

            }

            @Override
            public void onParticipantConnected(CallSession callSession, String userId) {

            }

            @Override
            public void onReceiveRemoteVideoTrack(CallSession callSession, String userId, VideoTrack videoTrack) {

            }

            @Override
            public void onParticipantLeft(CallSession callSession, String userId, CallEndReason reason) {

            }

            @Override
            public void onCallEnd(CallSession callSession, CallEndReason endReason) {

            }
        });
        if(!callSession.isAudioOnly()) {
            callSession.setVideoCapture(new FileVideoCapture(videoFilePath, callSession.getConversation(), callSession.getCallId()));
        }
    }

    public void startGroupCall(Conversation conversation, List<String> targets, boolean audioOnly, boolean advanceEngine) {
        CallSession callSession = AVEngineKit.getInstance().startGroupCall(conversation, targets, audioOnly, advanceEngine, new EchoAudioDevice(conversation), new CallEventCallback() {
            @Override
            public void onCallStateUpdated(CallSession callSession, CallState state) {

            }

            @Override
            public void onParticipantJoined(CallSession callSession, String userId) {

            }

            @Override
            public void onParticipantConnected(CallSession callSession, String userId) {

            }

            @Override
            public void onReceiveRemoteVideoTrack(CallSession callSession, String userId, VideoTrack videoTrack) {

            }

            @Override
            public void onParticipantLeft(CallSession callSession, String userId, CallEndReason reason) {

            }

            @Override
            public void onCallEnd(CallSession callSession, CallEndReason endReason) {

            }
        });
        if(!callSession.isAudioOnly()) {
            callSession.setVideoCapture(new FileVideoCapture(videoFilePath, callSession.getConversation(), callSession.getCallId()));
        }
    }

    public void onConferenceEvent(String event) {
        AVEngineKit.getInstance().onConferenceEvent(event);
    }

    public boolean onReceiveCallMessage(OutputMessageData messageData) {
        return AVEngineKit.getInstance().onReceiveCallMessage(messageData);
    }
}
