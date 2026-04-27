package cn.wildfirechat.app.call;

import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrackSink;

/**
 * A thin VideoTrackSink that forwards each frame to the shared VideoCompositor.
 */
public class ParticipantVideoSink implements VideoTrackSink {

    private final String userId;
    private final VideoCompositor compositor;

    public ParticipantVideoSink(String userId, VideoCompositor compositor) {
        this.userId = userId;
        this.compositor = compositor;
    }

    @Override
    public void onVideoFrame(VideoFrame frame) {
        compositor.updateFrame(userId, frame);
    }

    public String getUserId() {
        return userId;
    }
}
