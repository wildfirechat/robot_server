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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Service
public class CallService {
    private static final Logger LOG = LoggerFactory.getLogger(CallService.class);

    @Autowired
    private RobotConfig mRobotConfig;

    @Value("${ice.url:}")
    private String iceUrl;

    @Value("${ice.password:}")
    private String icePassword;

    @Value("${ice.username:}")
    private String iceUsername;

    @Value("${hls.output.base.path:/tmp/hls/}")
    private String hlsBasePath;

    @Value("${hls.output.width:640}")
    private int hlsOutputWidth;

    @Value("${hls.output.height:360}")
    private int hlsOutputHeight;

    @Value("${hls.output.fps:15}")
    private int hlsOutputFps;

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    /** HLS segment duration in seconds. Shorter = lower latency, more HTTP requests. */
    @Value("${hls.segment.time:1}")
    private int hlsSegmentTime;

    @Value("${video.file.path}")
    private String videoFilePath;

    /** Active HLS mixer instances, keyed by callId. Supports concurrent calls. */
    private final Map<String, HlsCallMixer> hlsMixerMap = Collections.synchronizedMap(new HashMap<>());

    /** Tracks whether each userId prefers the advance engine (for outbound call routing). */
    private final Map<String, Boolean> engineTypeMap = new HashMap<>();

    @PostConstruct
    private void init() {
        RobotService robotService = new RobotService(
                mRobotConfig.im_url, mRobotConfig.getIm_id(), mRobotConfig.im_secret);

        // 1. Initialise the AV SDK
        AVEngineKit.getInstance().init(robotService, new AVEngineKitCallback() {
            @Override
            public void onReceiveCall(CallSession callSession) {
                String callId = callSession.getCallId();
                LOG.info("onReceiveCall: {}", callId);

                for (String participant : callSession.getParticipants()) {
                    engineTypeMap.put(participant, callSession.isAdvanceEngine());
                }

                // Create and start the HLS mixer for this call
                HlsCallMixer mixer = new HlsCallMixer(
                        callId, hlsBasePath, hlsOutputWidth, hlsOutputHeight, hlsOutputFps, hlsSegmentTime, ffmpegPath);

                if (!mixer.start()) {
                    LOG.error("onReceiveCall: failed to start HlsCallMixer for call {}", callId);
                    return;
                }
                hlsMixerMap.put(callId, mixer);

                // The mixer receives all participant audio via AudioDevice callbacks
                callSession.setAudioDevice(mixer);
                if(!callSession.isAudioOnly()) {
                    callSession.setVideoCapture(new FileVideoCapture(videoFilePath, callSession.getConversation(), callSession.getCallId()));
                }

                callSession.setEventCallback(new CallEventCallback() {
                    @Override
                    public void onCallStateUpdated(CallSession callSession, CallState state) {
                        LOG.debug("onCallStateUpdated: {} -> {}", callSession.getCallId(), state);
                    }

                    @Override
                    public void onParticipantJoined(CallSession callSession, String userId) {
                        LOG.info("onParticipantJoined: {} in call {}", userId, callSession.getCallId());
                        HlsCallMixer m = hlsMixerMap.get(callSession.getCallId());
                        if (m != null) {
                            m.addParticipant(userId);
                        }
                    }

                    @Override
                    public void onParticipantConnected(CallSession callSession, String userId) {
                        LOG.info("onParticipantConnected: {} in call {}", userId, callSession.getCallId());
                    }

                    @Override
                    public void onReceiveRemoteVideoTrack(CallSession callSession, String userId, VideoTrack videoTrack) {
                        LOG.info("onReceiveRemoteVideoTrack: {} in call {}", userId, callSession.getCallId());
                        HlsCallMixer m = hlsMixerMap.get(callSession.getCallId());
                        if (m != null) {
                            // addParticipant is idempotent; ensures compositor slot exists
                            // even if onParticipantJoined fired before we mapped the video track
                            ParticipantVideoSink sink = m.addParticipant(userId);
                            videoTrack.addSink(sink);
                        }
                    }

                    @Override
                    public void onParticipantLeft(CallSession callSession, String userId, CallEndReason reason) {
                        LOG.info("onParticipantLeft: {} from call {} reason={}", userId, callSession.getCallId(), reason);
                        HlsCallMixer m = hlsMixerMap.get(callSession.getCallId());
                        if (m != null) {
                            m.removeParticipant(userId);
                        }
                    }

                    @Override
                    public void onCallEnd(CallSession callSession, CallEndReason endReason) {
                        String cid = callSession.getCallId();
                        LOG.info("onCallEnd: {} reason={}", cid, endReason);
                        HlsCallMixer m = hlsMixerMap.remove(cid);
                        if (m != null) {
                            m.stop();
                            LOG.info("HLS stream stopped for call {}, playlist was: {}",
                                    cid, m.getPlaylistUrlPath());
                        }
                    }
                });

                // Answer after a short delay so the remote side sees the ring
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    callSession.answer(callSession.isAudioOnly());
                    LOG.info("Answered call {} (audioOnly={})", callId, callSession.isAudioOnly());
                }, "call-answer-" + callId).start();
            }
        });

        // 2. Configure ICE/TURN servers (skip if not set; advance engine doesn't need them)
        if (!StringUtils.isEmpty(iceUrl)) {
            AVEngineKit.getInstance().addIceServer(iceUrl, iceUsername, icePassword);
        }

        // 3. WebRTC diagnostic logs – enable only when debugging
         AVEngineKit.getInstance().enableWebRTCLog();
//        FFmpegLogCallback.set();
    }

    // -------------------------------------------------------------------------
    // Public helpers used by Controller / Service
    // -------------------------------------------------------------------------

    public boolean hasPreferEngine(String userId) {
        boolean has = engineTypeMap.containsKey(userId);
        if (!has) {
            LOG.info("hasPreferEngine: false for {}, map={}", userId, engineTypeMap);
        }
        return has;
    }

    public boolean isAdvanceEngine(String userId) {
        return Boolean.TRUE.equals(engineTypeMap.get(userId));
    }

    /**
     * Returns a snapshot of all active HLS playlist URL paths.
     * Example entry: {@code "callId-abc" -> "/hls/callId-abc/stream.m3u8"}
     */
    public Map<String, String> getActiveHlsStreams() {
        Map<String, String> result = new HashMap<>();
        synchronized (hlsMixerMap) {
            for (HlsCallMixer mixer : hlsMixerMap.values()) {
                result.put(mixer.getCallId(), mixer.getPlaylistUrlPath());
            }
        }
        return result;
    }

    public void onConferenceEvent(String event) {
        AVEngineKit.getInstance().onConferenceEvent(event);
    }

    public boolean onReceiveCallMessage(OutputMessageData messageData) {
        return AVEngineKit.getInstance().onReceiveCallMessage(messageData);
    }

    // -------------------------------------------------------------------------
    // Outbound calls (robot initiates) – uses EchoAudioDevice (not HLS mixed)
    // -------------------------------------------------------------------------

    public void startPrivateCall(Conversation conversation, boolean audioOnly, boolean advanceEngine) {
//        AVEngineKit.getInstance().startPrivateCall(
//                conversation, audioOnly, advanceEngine,
//                new EchoAudioDevice(conversation),
//                new CallEventCallback() {
//                    @Override public void onCallStateUpdated(CallSession s, CallState st) {}
//                    @Override public void onParticipantJoined(CallSession s, String u) {}
//                    @Override public void onParticipantConnected(CallSession s, String u) {}
//                    @Override public void onReceiveRemoteVideoTrack(CallSession s, String u, VideoTrack t) {}
//                    @Override public void onParticipantLeft(CallSession s, String u, CallEndReason r) {}
//                    @Override public void onCallEnd(CallSession s, CallEndReason r) {}
//                });
    }

    public void startGroupCall(Conversation conversation, List<String> targets,
                               boolean audioOnly, boolean advanceEngine) {
//        AVEngineKit.getInstance().startGroupCall(
//                conversation, targets, audioOnly, advanceEngine,
//                new EchoAudioDevice(conversation),
//                new CallEventCallback() {
//                    @Override public void onCallStateUpdated(CallSession s, CallState st) {}
//                    @Override public void onParticipantJoined(CallSession s, String u) {}
//                    @Override public void onParticipantConnected(CallSession s, String u) {}
//                    @Override public void onReceiveRemoteVideoTrack(CallSession s, String u, VideoTrack t) {}
//                    @Override public void onParticipantLeft(CallSession s, String u, CallEndReason r) {}
//                    @Override public void onCallEnd(CallSession s, CallEndReason r) {}
//                });
    }
}
