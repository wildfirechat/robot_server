package cn.wildfirechat.app.call;

import cn.wildfirechat.AudioDevice;
import cn.wildfirechat.CallSession;
import dev.onvoid.webrtc.media.video.VideoTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-call coordinator that:
 * <ol>
 *   <li>Creates named pipes for raw video (YUV420p) and audio (s16le stereo).</li>
 *   <li>Starts an FFmpeg process that reads those pipes and writes HLS segments.</li>
 *   <li>Owns an {@link AudioMixer} and a {@link VideoCompositor} that produce the
 *       mixed streams and push them into the pipes.</li>
 *   <li>Implements {@link AudioDevice} so it receives per-participant audio callbacks.</li>
 * </ol>
 *
 * <p>Lifecycle:
 * <pre>
 *   mixer.start()                     // creates pipes, starts FFmpeg + mixer threads
 *   mixer.addParticipant(userId)       // returns a VideoTrackSink to attach to the VideoTrack
 *   mixer.removeParticipant(userId)    // participant left
 *   mixer.stop()                       // tears everything down
 * </pre>
 *
 * <p>HLS output directory: {@code <hlsBasePath>/<callId>/}
 * <br>Playlist URL (via Spring static serving): {@code http://host:port/hls/<callId>/stream.m3u8}
 */
public class HlsCallMixer implements AudioDevice {
    private static final Logger LOG = LoggerFactory.getLogger(HlsCallMixer.class);

    private final String callId;
    /** Absolute path to the directory where HLS files for this call are written. */
    private final String hlsOutputDir;
    private final int outputWidth;
    private final int outputHeight;
    private final int outputFps;
    private final int hlsSegmentTime;
    private final String ffmpegPath;

    private final String videoPipePath;
    private final String audioPipePath;

    private Process ffmpegProcess;
    private AudioMixer audioMixer;
    private VideoCompositor videoCompositor;

    /** Sinks keyed by userId so we can detach them when participants leave. */
    private final Map<String, ParticipantVideoSink> videoSinks = new ConcurrentHashMap<>();

    private volatile boolean started = false;

    public HlsCallMixer(String callId,
                        String hlsBasePath,
                        int outputWidth,
                        int outputHeight,
                        int outputFps,
                        int hlsSegmentTime,
                        String ffmpegPath) {
        this.callId          = callId;
        this.hlsOutputDir    = (hlsBasePath.endsWith("/") ? hlsBasePath : hlsBasePath + "/") + callId;
        this.outputWidth     = outputWidth  & ~1;
        this.outputHeight    = outputHeight & ~1;
        this.outputFps       = outputFps;
        this.hlsSegmentTime  = hlsSegmentTime;
        this.ffmpegPath      = ffmpegPath;

        String suffix       = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        this.videoPipePath  = "/tmp/hls_v_" + suffix;
        this.audioPipePath  = "/tmp/hls_a_" + suffix;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates pipes, starts the mixer threads and launches FFmpeg.
     * Must be called before answering the call.
     */
    public boolean start() {
        if (started) return true;

        try {
            Files.createDirectories(Paths.get(hlsOutputDir));
        } catch (IOException e) {
            LOG.error("HlsCallMixer[{}]: cannot create HLS directory {}", callId, hlsOutputDir, e);
            return false;
        }

        if (!createPipes()) return false;

        audioMixer      = new AudioMixer(audioPipePath);
        videoCompositor = new VideoCompositor(videoPipePath, outputWidth, outputHeight, outputFps);

        // Mixer threads start first; they will block on opening the FIFO for writing
        // until FFmpeg opens the read end.
        audioMixer.start();
        videoCompositor.start();

        if (!startFFmpeg()) {
            audioMixer.stop();
            videoCompositor.stop();
            deletePipes();
            return false;
        }

        started = true;
        LOG.info("HlsCallMixer[{}]: started – HLS at {}/stream.m3u8", callId, hlsOutputDir);
        return true;
    }

    /** Tears down FFmpeg, mixer threads, and named pipes. */
    public void stop() {
        if (!started) return;
        started = false;

        LOG.info("HlsCallMixer[{}]: stopping", callId);

        audioMixer.stop();
        videoCompositor.stop();

        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            ffmpegProcess.destroy();
            try {
                if (!ffmpegProcess.waitFor(5, TimeUnit.SECONDS)) {
                    ffmpegProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                ffmpegProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        deletePipes();
        LOG.info("HlsCallMixer[{}]: stopped", callId);
    }

    // -------------------------------------------------------------------------
    // Participant management
    // -------------------------------------------------------------------------

    /**
     * Register a participant and return a {@link ParticipantVideoSink} that the caller
     * must attach to the participant's {@link VideoTrack} via {@code videoTrack.addSink(sink)}.
     */
    public ParticipantVideoSink addParticipant(String userId) {
        audioMixer.addParticipant(userId);
        videoCompositor.addParticipant(userId);
        ParticipantVideoSink sink = new ParticipantVideoSink(userId, videoCompositor);
        videoSinks.put(userId, sink);
        LOG.info("HlsCallMixer[{}]: participant {} joined", callId, userId);
        return sink;
    }

    public void removeParticipant(String userId) {
        audioMixer.removeParticipant(userId);
        videoCompositor.removeParticipant(userId);
        videoSinks.remove(userId);
        LOG.info("HlsCallMixer[{}]: participant {} left", callId, userId);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Absolute filesystem path to the HLS playlist file. */
    public String getPlaylistFilePath() {
        return hlsOutputDir + "/stream.m3u8";
    }

    /**
     * URL path segment for this call's playlist, relative to the static resource root.
     * Example: {@code /hls/call-123/stream.m3u8}
     */
    public String getPlaylistUrlPath() {
        return "/hls/" + callId + "/stream.m3u8";
    }

    public String getCallId() {
        return callId;
    }

    // -------------------------------------------------------------------------
    // AudioDevice implementation
    // -------------------------------------------------------------------------

    @Override
    public int initPlayout(CallSession callSession, String userId) {
        return 0;
    }

    @Override
    public int stopPlayout(CallSession callSession, String userId) {
        return 0;
    }

    @Override
    public int initRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int startRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int stopRecording(CallSession callSession) {
        return 0;
    }

    /**
     * The robot/server has nothing to say – send silence so WebRTC keeps the session alive.
     */
    @Override
    public void fetchRecordData(CallSession callSession,
                                byte[] sampleData,
                                int nSamples,
                                int nSampleBytes,
                                int nChannels,
                                int nSampleRate,
                                int nBuffSize) {
        Arrays.fill(sampleData, 0, nBuffSize, (byte) 0);
    }

    /**
     * Receives decoded PCM audio from a remote participant and forwards it to the AudioMixer.
     */
    @Override
    public void playoutData(CallSession callSession, String userId, byte[] sampleData, int nBuffSize) {
        if (started) {
            audioMixer.onAudioData(userId, sampleData, nBuffSize);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean createPipes() {
        try {
            deletePipes(); // clean up any stale pipes from a previous run
            String[] mkfifoV = {"mkfifo", "-m", "0666", videoPipePath};
            String[] mkfifoA = {"mkfifo", "-m", "0666", audioPipePath};
            Process pv = new ProcessBuilder(mkfifoV).start();
            Process pa = new ProcessBuilder(mkfifoA).start();
            if (pv.waitFor() != 0 || pa.waitFor() != 0) {
                LOG.error("HlsCallMixer[{}]: mkfifo failed", callId);
                return false;
            }
            return true;
        } catch (Exception e) {
            LOG.error("HlsCallMixer[{}]: failed to create named pipes", callId, e);
            return false;
        }
    }

    private void deletePipes() {
        try { Files.deleteIfExists(Paths.get(videoPipePath)); } catch (IOException ignored) {}
        try { Files.deleteIfExists(Paths.get(audioPipePath)); } catch (IOException ignored) {}
    }

    private boolean startFFmpeg() {
        String playlistPath = hlsOutputDir + "/stream.m3u8";
        // Keyframe interval = 1 segment duration → clean cuts, no seeking stutter
        int keyframeInterval = outputFps * hlsSegmentTime;
        String[] cmd = {
            ffmpegPath, "-y", "-nostdin",
            // Video input: raw YUV420p at the configured resolution and frame rate
            "-f", "rawvideo", "-pix_fmt", "yuv420p",
            "-s", outputWidth + "x" + outputHeight,
            "-r", String.valueOf(outputFps),
            "-i", videoPipePath,
            // Audio input: raw s16le stereo at 48 kHz
            "-f", "s16le", "-ar", "48000", "-ac", "2",
            "-i", audioPipePath,
            // Video encode – ultrafast + zerolatency for minimum pipeline delay
            "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency",
            "-g", String.valueOf(keyframeInterval),
            "-sc_threshold", "0",   // disable scene-change splits for predictable segment length
            "-keyint_min", String.valueOf(keyframeInterval),
            // Audio encode
            "-c:a", "aac", "-b:a", "128k",
            // HLS output – 1-second segments, keep 6, delete old ones
            "-f", "hls",
            "-hls_time", String.valueOf(hlsSegmentTime),
            "-hls_list_size", "6",
            "-hls_flags", "delete_segments+split_by_time",
            "-hls_segment_type", "mpegts",
            playlistPath
        };

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            ffmpegProcess = pb.start();

            Thread logThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(ffmpegProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOG.debug("[ffmpeg:{}] {}", callId, line);
                    }
                } catch (IOException ignored) {}
            }, "ffmpeg-log-" + callId);
            logThread.setDaemon(true);
            logThread.start();

            LOG.info("HlsCallMixer[{}]: FFmpeg started, command: {}", callId, String.join(" ", cmd));
            return true;
        } catch (IOException e) {
            LOG.error("HlsCallMixer[{}]: failed to launch FFmpeg", callId, e);
            return false;
        }
    }
}
