package cn.wildfirechat.app.call;

import cn.wildfirechat.*;
import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.voiceagent.AiAudioDevice;
import cn.wildfirechat.app.voiceagent.VoiceAgentFactory;
import cn.wildfirechat.impl.SignalServerImpl;
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
import java.util.concurrent.ConcurrentHashMap;

@org.springframework.stereotype.Service
public class CallService {
    private static final Logger LOG = LoggerFactory.getLogger(CallService.class);
    @Autowired
    private RobotConfig mRobotConfig;

    @Autowired
    private VoiceAgentFactory voiceAgentFactory;

    @Value("${ice.url}")
    private String iceUrl;

    @Value("${ice.password}")
    private String icePassword;

    @Value("${ice.username}")
    private String iceUsername;

    @Value("${video.file.path}")
    private String videoFilePath;

    //是否只发送音视频，不接收对方的音视频流（仅对高级版音视频有效）
    @Value("${call.send.only:false}")
    private boolean sendOnly;

    //是否定期抓取对端视频截屏保存为bmp文件（调试用，会产生大量磁盘文件）
    @Value("${video.snapshot.enabled:true}")
    private boolean videoSnapshotEnabled;

    @Value("${public.ipv4:}")
    private String publicIpV4;

    @Value("${public.ipv6:}")
    private String publicIpV6;

    //音视频高级版janus服务地址替换关系（仅高级版音视频使用）。当本服务与janus服务在同一内网时，
    //把SDP中janus的公网IP替换为内网IP。格式：公网IP:内网IP，多组之间用英文逗号分隔
    @Value("${sdp.ip.replace.map:}")
    private String sdpIpReplaceMap;

    // ConcurrentHashMap: entries are removed on call end, otherwise the map would grow forever
    private Map<String, ImageVideoSink> imageVideoSinkMap = new ConcurrentHashMap<>();

    private final Map<String, Boolean> engineTypeMap = new HashMap<>();

    //新版avenginekit不再使用单例，每个机器人一个实例
    private AVEngineKit avEngineKit;

    @PostConstruct
    private void init() {
        RobotService robotService = new RobotService(mRobotConfig.im_url, mRobotConfig.getIm_id(), mRobotConfig.im_secret);

        //1. 初始化音视频SDK
        avEngineKit = new AVEngineKit();
        avEngineKit.init(mRobotConfig.getIm_id(), new SignalServerImpl(robotService), new AVEngineKitCallback() {
            @Override
            public void onReceiveCall(CallSession callSession) {
                LOG.info("onReceiveCall: {}", callSession.getCallId());
                for (String participant : callSession.getParticipants()) {
                    engineTypeMap.put(participant, callSession.isAdvanceEngine());
                }
                LOG.info("engineTypeMap: {}, {}", engineTypeMap.size(), engineTypeMap);

                final AudioDevice audioDevice = createAudioDevice(callSession.getConversation());

                callSession.setEventCallback(new CallEventCallback() {
                    @Override
                    public void onCallStateUpdated(CallSession callSession, CallState state) {
                        // 接通之后才说开场白，太早对方还听不到
                        if (state == CallState.kWFAVEngineStateConnected && audioDevice instanceof AiAudioDevice) {
                            ((AiAudioDevice) audioDevice).getSession().greet();
                        }
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
                        ImageVideoSink imageVideoSink = new ImageVideoSink(userId, callSession.getCallId(), videoSnapshotEnabled);
                        ImageVideoSink existing = imageVideoSinkMap.putIfAbsent(key, imageVideoSink);
                        if(existing == null) {
                            videoTrack.addSink(imageVideoSink);
                        } else {
                            // Not used; let its worker thread exit instead of leaking it
                            imageVideoSink.onCallEnded();
                        }
                    }

                    @Override
                    public void onParticipantLeft(CallSession callSession, String userId, CallEndReason reason) {

                    }

                    @Override
                    public void onCallEnd(CallSession callSession, CallEndReason endReason) {
                        String callId = callSession.getCallId();
                        if (audioDevice instanceof AiAudioDevice) {
                            ((AiAudioDevice) audioDevice).close();
                        }
                        // Stop and remove this call's sinks so the map doesn't grow across calls
                        imageVideoSinkMap.values().removeIf(value -> {
                            if(value.callId.equals(callId)) {
                                value.onCallEnded();
                                return true;
                            }
                            return false;
                        });
                    }
                });

                // for rtsp
//                RtspCapturer capturer =  RtspCapturer.createAndProbe("rtsp://192.168.2.186:8554/live/stream1");
//                callSession.setAudioDevice(capturer);
//                if(!callSession.isAudioOnly()) {
//                    callSession.setVideoCapture(capturer);
//                }

                callSession.setAudioDevice(audioDevice);
                if(!callSession.isAudioOnly()) {
                    callSession.setVideoCapture(new FileVideoCapture(videoFilePath, callSession.getConversation(), callSession.getCallId()));
                }

                //延迟接听，时长可配（agent.answerDelayMs）
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(voiceAgentFactory.getAnswerDelayMs());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        callSession.answer(callSession.isAudioOnly(), sendOnly);
                    }
                }).start();
            }
        });


        //2. 设置turn服务地址，如果有多个，可以调用多次。如果是高级版，可以不用设置turn服务。全局生效，只需设置一次
        if(!StringUtils.isEmpty(iceUrl)) {
            //如果是高级版，不用设置turn服务。
            AVEngineKit.addIceServer(iceUrl, iceUsername, icePassword);
        }

        //3. 设置公网IP地址。只有使用免费版音视频且服务拥有公网IP地址时才需要调用。
        AVEngineKit.setPublicIp(publicIpV4, publicIpV6);

        //4. 设置janus服务地址替换关系。只有使用高级版音视频且本服务与janus服务在同一内网时才需要调用。全局生效，只需设置一次
        if(!StringUtils.isEmpty(sdpIpReplaceMap)) {
            Map<String, String> ipReplaceMap = new HashMap<>();
            for (String pair : sdpIpReplaceMap.split(",")) {
                String[] kv = pair.trim().split(":");
                if(kv.length == 2) {
                    ipReplaceMap.put(kv[0].trim(), kv[1].trim());
                }
            }
            AVEngineKit.setRemoteSdpIpReplaceMap(ipReplaceMap);
        }

        //5. 打开webrtc的日志，一般不用打开，除非出现问题需要debug
        //AVEngineKit.enableWebRTCLog();
    }

    /**
     * 语音 Agent 开着就用 AI 音频设备，关掉退回原来的 EchoAudioDevice（延迟 3 秒复读）。
     * 保留复读机是为了排查：如果 AI 不出声，先把开关关掉打一通，能复读就说明
     * 通话链路本身是通的，问题在 ASR/LLM/TTS 那一段。
     */
    private AudioDevice createAudioDevice(Conversation conversation) {
        if (voiceAgentFactory.isEnabled()) {
            return voiceAgentFactory.createDevice(conversation);
        }
        return new EchoAudioDevice(conversation);
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
        final AudioDevice audioDevice = createAudioDevice(conversation);
        CallSession callSession = avEngineKit.startPrivateCall(conversation, audioOnly, advanceEngine, sendOnly, audioDevice, new CallEventCallback() {
            @Override
            public void onCallStateUpdated(CallSession callSession, CallState state) {
                if (state == CallState.kWFAVEngineStateConnected && audioDevice instanceof AiAudioDevice) {
                    ((AiAudioDevice) audioDevice).getSession().greet();
                }
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
                if (audioDevice instanceof AiAudioDevice) {
                    ((AiAudioDevice) audioDevice).close();
                }
            }
        }, 0, null);
        if(!callSession.isAudioOnly()) {
            callSession.setVideoCapture(new FileVideoCapture(videoFilePath, callSession.getConversation(), callSession.getCallId()));
        }
    }

    public void startGroupCall(Conversation conversation, List<String> targets, boolean audioOnly, boolean advanceEngine) {
        final AudioDevice audioDevice = createAudioDevice(conversation);
        CallSession callSession = avEngineKit.startGroupCall(conversation, targets, audioOnly, advanceEngine, sendOnly, audioDevice, new CallEventCallback() {
            @Override
            public void onCallStateUpdated(CallSession callSession, CallState state) {
                if (state == CallState.kWFAVEngineStateConnected && audioDevice instanceof AiAudioDevice) {
                    ((AiAudioDevice) audioDevice).getSession().greet();
                }
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
                if (audioDevice instanceof AiAudioDevice) {
                    ((AiAudioDevice) audioDevice).close();
                }
            }
        }, 0, null);
        if(!callSession.isAudioOnly()) {
            callSession.setVideoCapture(new FileVideoCapture(videoFilePath, callSession.getConversation(), callSession.getCallId()));
        }
    }

    public void onConferenceEvent(String event) {
        avEngineKit.onConferenceEvent(event);
    }

    public boolean onReceiveCallMessage(OutputMessageData messageData) {
        return avEngineKit.onReceiveCallMessage(messageData);
    }
}
