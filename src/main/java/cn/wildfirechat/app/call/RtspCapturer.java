package cn.wildfirechat.app.call;

import cn.wildfirechat.AudioDevice;
import cn.wildfirechat.CallSession;
import dev.onvoid.webrtc.media.video.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.*;

import cn.wildfirechat.app.call.RtspMetadataProber.VideoInfo;
import cn.wildfirechat.app.call.RtspMetadataProber.AudioInfo;

public class RtspCapturer implements JavaVideoCapture, AudioDevice {

    private static final Logger logger = LoggerFactory.getLogger(RtspCapturer.class);
    private final String rtspUrl;
    private volatile VideoCaptureCapability captureFormat; // Made non-final, volatile for visibility
    private volatile Process ffmpegProcess; // volatile for visibility
    private volatile boolean isRunning = false; // Controls main loops of readers/monitor, volatile
    private static final int FFPROBE_TIMEOUT_SECONDS = 10;

    private final BlockingQueue<byte[]> videoCacheQueue = new LinkedBlockingQueue<>(100);
    private volatile int videoFrameSize; // Will be updated if captureFormat changes, volatile

    private final AudioInfo probedAudioInfo;
    private volatile int currentFfmpegAudioSampleRate;
    private volatile int currentFfmpegAudioChannels;
    private volatile boolean ffmpegAudioInitialized = false;

    private volatile AudioCaptureParameters audioCaptureParameters; // Contains what WebRTC expects for fetchRecordData, volatile
    private final BlockingQueue<byte[]> audioCacheQueue = new LinkedBlockingQueue<>(100);

    private String videoPipePath;
    private String audioPipePath;

    // Executor for video/audio pipe readers and ffmpeg process monitor
    private final ExecutorService mediaExecutorService = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    // Futures to manage the threads
    private volatile Future<?> videoReaderFuture;
    private volatile Future<?> audioReaderFuture;
    private volatile Future<?> ffmpegMonitorFuture;

    private static class AudioCaptureParameters {
        final int sampleRate;
        final int channels;
        final int bufferSize; // nBuffSize from fetchRecordData

        AudioCaptureParameters(int sampleRate, int channels, int bufferSize) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bufferSize = bufferSize;
        }
    }

    private RtspCapturer(String rtspUrl, VideoInfo captureFormat, AudioInfo audioInfo) {
        this.rtspUrl = rtspUrl;
        this.captureFormat = new VideoCaptureCapability(captureFormat.width, captureFormat.height, captureFormat.frameRate); // Initial video format
        if (this.captureFormat != null) {
            this.videoFrameSize = captureFormat.width * captureFormat.height * 3 / 2;
        } else {
            logger.warn("RtspVideoCapturer created with null initial captureFormat. Video will not work until onStartCapture.");
            this.videoFrameSize = 0;
        }

        this.probedAudioInfo = audioInfo;

        if (this.probedAudioInfo != null) {
            this.currentFfmpegAudioSampleRate = this.probedAudioInfo.sampleRate;
            this.currentFfmpegAudioChannels = this.probedAudioInfo.channels;
        } else {
            this.currentFfmpegAudioSampleRate = 48000; // Default placeholder
            this.currentFfmpegAudioChannels = 1;    // Default placeholder
            logger.warn("AudioInfo not provided or probing failed. Initial FFmpeg audio params will be defaults: SampleRate={}, Channels={}.", this.currentFfmpegAudioSampleRate, this.currentFfmpegAudioChannels);
        }

        String pipeSuffix = UUID.randomUUID().toString().substring(0, 8);
        this.videoPipePath = "/tmp/video_pipe_" + pipeSuffix;
        this.audioPipePath = "/tmp/audio_pipe_" + pipeSuffix;
    }

    public static RtspCapturer createAndProbe(String rtspUrl) {
        logger.debug("Attempting to probe RTSP stream: {}", rtspUrl);

        VideoInfo videoInfo = RtspMetadataProber.probeStream(rtspUrl, FFPROBE_TIMEOUT_SECONDS);
        if (videoInfo != null) {
            logger.info("Successfully probed RTSP stream video: {}", videoInfo);
        } else {
            logger.warn("Failed to probe video metadata for RTSP stream: {}. Video parameters will be taken from onStartCapture.", rtspUrl);
        }

        RtspMetadataProber.AudioInfo audioInfo = null;
        try {
            audioInfo = RtspMetadataProber.probeAudioInfo(rtspUrl, FFPROBE_TIMEOUT_SECONDS);
            if (audioInfo == null) {
                logger.warn("Failed to probe audio metadata for RTSP stream: {}. Will use default audio params for initial FFmpeg setup.", rtspUrl);
            } else {
                logger.info("Successfully probed RTSP stream audio: {}", audioInfo);
            }
        } catch (Exception e) {
            logger.error("Exception during audio probing for {}: {}. Will use default audio params.", rtspUrl, e.getMessage(), e);
        }
        // Pass potentially null videoInfo, constructor and onStartCapture will handle it.
        return new RtspCapturer(rtspUrl, videoInfo, audioInfo);
    }

    private boolean createNamedPipes() {
        try {
            logger.debug("Creating named pipes: Video={}, Audio={}", videoPipePath, audioPipePath);
            // Ensure that the 'mkfifo' command is available and works in the execution environment.
            // This might need adjustment for Windows (e.g., using different mechanisms for named pipes).
            Process pVideo = Runtime.getRuntime().exec("mkfifo " + videoPipePath);
            Process pAudio = Runtime.getRuntime().exec("mkfifo " + audioPipePath);
            if (pVideo.waitFor() != 0) {
                logger.error("Failed to create video pipe, mkfifo exit code: {}", pVideo.exitValue());
                return false;
            }
            if (pAudio.waitFor() != 0) {
                logger.error("Failed to create audio pipe, mkfifo exit code: {}", pAudio.exitValue());
                // Clean up video pipe if audio pipe creation fails
                Files.deleteIfExists(Paths.get(videoPipePath));
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to create named pipes", e);
            Thread.currentThread().interrupt(); // Restore interrupted status
            return false;
        }
    }

    private void deleteNamedPipes() {
        try {
            logger.debug("Deleting named pipes: Video={}, Audio={}", videoPipePath, audioPipePath);
            Files.deleteIfExists(Paths.get(videoPipePath));
            Files.deleteIfExists(Paths.get(audioPipePath));
        } catch (IOException e) {
            logger.warn("Failed to delete named pipes", e);
        }
    }

    @Override
    public VideoCaptureCapability getCapability() {
        VideoCaptureCapability currentFormat = this.captureFormat; // Read volatile field once
        return currentFormat != null ? new VideoCaptureCapability(currentFormat.width, currentFormat.height, currentFormat.frameRate) : null;
    }

    @Override
    public int onInit(String label) {
        logger.debug("Video initialize called. Label: {}", label);
        return 0;
    }

    private void cancelFuture(Future<?> future, String name) {
        if (future != null && !future.isDone()) {
            logger.debug("Cancelling {} thread...", name);
            future.cancel(true); // Interrupt if running
        }
    }

    private synchronized void stopFFmpegAndReadersInternal(boolean retainPipes) {
        logger.debug("Stopping FFmpeg and readers (internal). Retain pipes: {}", retainPipes);
        isRunning = false; // Primary signal for all loops to stop

        // 1. Stop FFmpeg process
        Process currentProcess = this.ffmpegProcess;
        if (currentProcess != null) {
            if (currentProcess.isAlive()) {
                logger.debug("Destroying FFmpeg process...");
                currentProcess.destroy();
                try {
                    if (!currentProcess.waitFor(2, TimeUnit.SECONDS)) {
                        logger.warn("FFmpeg process did not exit gracefully, forcing destroy.");
                        currentProcess.destroyForcibly();
                        if (!currentProcess.waitFor(1, TimeUnit.SECONDS)) {
                            logger.error("FFmpeg process could not be forcibly destroyed.");
                        }
                    }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for FFmpeg process to exit. Forcing destroy.");
                    if (currentProcess.isAlive()) currentProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            this.ffmpegProcess = null;
        }

        // 2. Cancel reader/monitor threads
        // Make copies of volatile fields before checking/using
        Future<?> videoFuture = this.videoReaderFuture;
        Future<?> audioFuture = this.audioReaderFuture;
        Future<?> monitorFuture = this.ffmpegMonitorFuture;

        cancelFuture(videoFuture, "VideoReader");
        this.videoReaderFuture = null;
        cancelFuture(audioFuture, "AudioReader");
        this.audioReaderFuture = null;
        cancelFuture(monitorFuture, "FFmpegMonitor");
        this.ffmpegMonitorFuture = null;

        // Brief pause to allow threads to see isRunning = false and exit loops
        try {
            Thread.sleep(50); // Small delay, adjust if necessary
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Sleep interrupted after signaling threads to stop.");
        }

        // 3. Clear queues
        videoCacheQueue.clear();
        audioCacheQueue.clear();

        // 4. Delete pipes if not retaining
        if (!retainPipes) {
            deleteNamedPipes();
        }
        ffmpegAudioInitialized = false; // Reset audio initialization status
        logger.debug("FFmpeg and readers stopped (internal). isRunning is now {}", isRunning);
    }

    // Centralized method to start FFmpeg and readers
    private synchronized boolean startFFmpegAndReaders(VideoCaptureCapability videoParams, int audioSampleRate, int audioChannels) {
        if (videoParams == null || videoParams.width <= 0 || videoParams.height <= 0 || videoParams.frameRate <= 0) {
            logger.error("Cannot start FFmpeg: Invalid video parameters provided: {}", videoParams);
            return false;
        }
        if (audioSampleRate <= 0 || audioChannels <= 0) {
            logger.error("Cannot start FFmpeg: Invalid audio parameters provided: SampleRate={}, Channels={}", audioSampleRate, audioChannels);
            return false;
        }

        logger.info("Attempting to start/restart FFmpeg. Video: {}x{} @{}fps, Audio: {}Hz {}ch",
            videoParams.width, videoParams.height, videoParams.frameRate, audioSampleRate, audioChannels);

        stopFFmpegAndReadersInternal(true); // Stop existing, retain pipes for now (will be deleted and recreated next)

        this.captureFormat = videoParams;
        this.videoFrameSize = videoParams.width * videoParams.height * 3 / 2;
        this.currentFfmpegAudioSampleRate = audioSampleRate;
        this.currentFfmpegAudioChannels = audioChannels;

        deleteNamedPipes();
        if (!createNamedPipes()) {
            logger.error("Failed to create named pipes. Cannot start FFmpeg.");
            return false;
        }

        int initialReaderBufferSize = this.currentFfmpegAudioSampleRate * this.currentFfmpegAudioChannels * 2 * 20 / 1000; // 20ms
        if (initialReaderBufferSize == 0) initialReaderBufferSize = (48000 * 1 * 2 * 20 / 1000); // Fallback to 48kHz mono 20ms
        this.audioCaptureParameters = new AudioCaptureParameters(this.currentFfmpegAudioSampleRate, this.currentFfmpegAudioChannels, initialReaderBufferSize);
        logger.info("Initialized audioCaptureParameters for reader: SR={}, CH={}, BS={}", this.currentFfmpegAudioSampleRate, this.currentFfmpegAudioChannels, initialReaderBufferSize);

        isRunning = true;

        videoReaderFuture = mediaExecutorService.submit(() -> {
            Thread.currentThread().setName("rtsp-video-pipe-reader");
            logger.debug("Video reader thread started for pipe: {}, frameSize: {}", videoPipePath, videoFrameSize);
            try (InputStream videoStream = new FileInputStream(videoPipePath)) {
                byte[] frameBuffer = new byte[videoFrameSize]; // Create buffer once if frameSize is fixed
                while (isRunning) {
                    if (videoFrameSize <= 0) { // Should not happen if startFFmpegAndReaders validates params
                        logger.warn("Video frame size is invalid ({}). Video reader pausing.", videoFrameSize);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    int totalBytesRead = 0;
                    while (totalBytesRead < videoFrameSize && isRunning) {
                        int bytesReadThisTurn = videoStream.read(frameBuffer, totalBytesRead, videoFrameSize - totalBytesRead);
                        if (bytesReadThisTurn == -1) {
                            if (isRunning) logger.warn("Video pipe End-Of-Stream reached.");
                            isRunning = false;
                            break;
                        }
                        totalBytesRead += bytesReadThisTurn;
                    }
                    if (!isRunning) break;
                    if (totalBytesRead == videoFrameSize) {
                        if (!videoCacheQueue.offer(frameBuffer, 100, TimeUnit.MILLISECONDS)) {
                            logger.warn("Video cache queue full, dropping frame.");
                        }
                    } else if (totalBytesRead > 0) {
                        logger.warn("Incomplete video frame: {}/{}", totalBytesRead, videoFrameSize);
                    }
                }
            } catch (Exception e) {
                if (isRunning) logger.error("Error during video pipe reading.", e);
            } finally {
                logger.debug("Video reader thread finished. isRunning: {}", isRunning);
                if (isRunning) isRunning = false;
            }
        });

        audioReaderFuture = mediaExecutorService.submit(() -> {
            Thread.currentThread().setName("rtsp-audio-pipe-reader");
            logger.debug("Audio reader thread started for pipe: {}", audioPipePath);
            try (InputStream audioStream = new FileInputStream(audioPipePath)) {
                while (isRunning) {
                    AudioCaptureParameters currentAudioParams = this.audioCaptureParameters; // Read volatile field once per loop
                    if (currentAudioParams == null || currentAudioParams.bufferSize <= 0) {
                        logger.warn("Audio reader: audioCaptureParameters not ready or invalid buffer size. Waiting...");
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    byte[] audioBuffer = new byte[currentAudioParams.bufferSize];
                    int totalBytesRead = 0;
                    while (totalBytesRead < audioBuffer.length && isRunning) {
                        int bytesReadThisTurn = audioStream.read(audioBuffer, totalBytesRead, audioBuffer.length - totalBytesRead);
                        if (bytesReadThisTurn == -1) {
                            if (isRunning) logger.warn("Audio pipe End-Of-Stream reached.");
                            isRunning = false;
                            break;
                        }
                        totalBytesRead += bytesReadThisTurn;
                    }
                    if (!isRunning) break;
                    if (totalBytesRead == audioBuffer.length) {
                        if (!audioCacheQueue.offer(audioBuffer, 100, TimeUnit.MILLISECONDS)) {
                            logger.warn("Audio cache queue full, dropping audio packet.");
                        }
                    } else if (totalBytesRead > 0) {
                        logger.warn("Incomplete audio packet: {}/{}", totalBytesRead, audioBuffer.length);
                    }
                }
            } catch (Exception e) {
                if (isRunning) logger.error("Error during audio pipe reading.", e);
            } finally {
                logger.debug("Audio reader thread finished. isRunning: {}", isRunning);
                if (isRunning) isRunning = false;
            }
        });

        ffmpegMonitorFuture = mediaExecutorService.submit(() -> {
            Thread.currentThread().setName("rtsp-ffmpeg-process-monitor");
            Process localFfmpegProcess = null; // To avoid race if this.ffmpegProcess is nulled by stop
            try {
                String ffmpegCommand = String.format(
                    "ffmpeg -y -nostdin -rtsp_transport tcp -i %s " +
                        "-vf fps=%d,scale=%d:%d -c:v rawvideo -pix_fmt yuv420p -f rawvideo %s " +
                        "-c:a pcm_s16le -ar %d -ac %d -f s16le %s",
                    rtspUrl,
                    videoParams.frameRate, videoParams.width, videoParams.height, videoPipePath,
                    this.currentFfmpegAudioSampleRate, this.currentFfmpegAudioChannels, audioPipePath
                );
                logger.info("Executing FFmpeg command: {}", ffmpegCommand);
                ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
                processBuilder.redirectErrorStream(true);
                localFfmpegProcess = processBuilder.start();
                this.ffmpegProcess = localFfmpegProcess; // Assign to instance field

                try (InputStream ffmpegLogs = localFfmpegProcess.getInputStream();
                     java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(ffmpegLogs))) {
                    String line;
                    // Check isRunning and localFfmpegProcess.isAlive() to ensure we only log while active
                    while (isRunning && localFfmpegProcess.isAlive() && (line = reader.readLine()) != null) {
                        logger.info("FFMPEG: {}", line);
                    }
                }
                int exitCode = localFfmpegProcess.waitFor();
                if (isRunning) { // If FFmpeg exits while we are still supposed to be running
                    logger.warn("FFmpeg process exited unexpectedly with code: {}", exitCode);
                }
            } catch (InterruptedException e) {
                logger.warn("FFmpeg process thread interrupted.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (isRunning) logger.error("Error during FFmpeg process execution.", e);
            } finally {
                logger.debug("FFmpeg process monitoring finished. isRunning: {}", isRunning);
                if (isRunning) isRunning = false;
                if (this.ffmpegProcess == localFfmpegProcess) { // Avoid issues if stop already nulled it
                    this.ffmpegProcess = null;
                }
            }
        });

        ffmpegAudioInitialized = true;
        logger.info("FFmpeg and readers started/restarted successfully.");
        return true;
    }


    @Override
    public int onStartCapture(int width, int height, int frameRate) {
        logger.debug("onStartCapture called with Video: {}x{} @{}fps", width, height, frameRate);

        VideoCaptureCapability requestedVideoParams = new VideoCaptureCapability(width, height, frameRate);
        VideoCaptureCapability currentVideoParams = this.captureFormat; // Read volatile once

        // Determine initial audio parameters for this start attempt
        // Prefer probed if available and FFmpeg hasn't been initialized with specific params yet by WebRTC
        int audioSR = (this.probedAudioInfo != null && this.probedAudioInfo.sampleRate > 0) ? this.probedAudioInfo.sampleRate : this.currentFfmpegAudioSampleRate; // Use current if already set by WebRTC, else probed
        int audioCH = (this.probedAudioInfo != null && this.probedAudioInfo.channels > 0) ? this.probedAudioInfo.channels : this.currentFfmpegAudioChannels;
        if (audioSR <= 0) audioSR = 48000; // Ensure valid defaults
        if (audioCH <= 0) audioCH = 1;

        // Restart if: not running, OR video params changed, OR audio not yet initialized by FFmpeg itself
        Process currentProc = this.ffmpegProcess; // Read volatile once
        if (currentProc == null || !currentProc.isAlive() ||
            !requestedVideoParams.equals(currentVideoParams) ||
            !ffmpegAudioInitialized) {

            logger.info("onStartCapture: Conditions met for starting/restarting FFmpeg. RequestedVideo: {}, CurrentVideo: {}, FfmpegRunning: {}, AudioInitialized: {}",
                requestedVideoParams, currentVideoParams, (currentProc != null && currentProc.isAlive()), ffmpegAudioInitialized);
            if (!startFFmpegAndReaders(requestedVideoParams, audioSR, audioCH)) {
                logger.error("Failed to start FFmpeg in onStartCapture.");
                return -1;
            }
        } else {
            logger.debug("onStartCapture: FFmpeg already running with compatible parameters.");
        }
        return 0;
    }


    @Override
    public int onStopCapture() {
        logger.debug("onStopCapture called.");
        stopFFmpegAndReadersInternal(false); // Full stop and cleanup pipes
        logger.debug("onStopCapture finished.");
        return 0;
    }

    @Override
    public int onFetchFrame(byte[] bytes, int width, int height, int frameRate, int rotation) { // Video frames
        if (!isRunning && videoCacheQueue.isEmpty()) {
            return -1; // Indicate capturer stopped and no more frames
        }
        try {
            byte[] data = videoCacheQueue.poll(10, TimeUnit.MILLISECONDS); // Non-blocking with timeout
            if (data != null) {
                if (data.length == bytes.length) {
                    System.arraycopy(data, 0, bytes, 0, bytes.length);
                    return 0; // Success
                } else {
                    logger.warn("Video data size error. Expected: {}, Got: {}. Dropping frame.", bytes.length, data.length);
                    return -1; // Error, indicate bad frame
                }
            }
            // No new frame available yet, but still running or frames might still be in queue
            return isRunning || !videoCacheQueue.isEmpty() ? 1 : -1;
        } catch (InterruptedException e) {
            logger.warn("Video frame fetch interrupted.", e);
            Thread.currentThread().interrupt();
            return -1; // Error
        }
    }

    // --- AudioDevice Implementation ---

    @Override
    public int initPlayout(CallSession callSession, String userId) {
        // Not used for a capture-only device
        return 0;
    }

    @Override
    public int stopPlayout(CallSession callSession, String userId) {
        // Not used for a capture-only device
        return 0;
    }

    @Override
    public int initRecording(CallSession callSession) {
        logger.debug("Audio initRecording called.");
        // This is where WebRTC might inform us about its expected audio format.
        // We've already started FFmpeg with a fixed format (pcm_s16le, specific rate/channels).
        // The crucial part is to get the buffer size WebRTC expects for fetchRecordData.
        // However, the AudioDevice API doesn't directly provide this in initRecording.
        // It's provided in fetchRecordData itself.
        // We'll store it when fetchRecordData is first called.
        return 0;
    }

    @Override
    public int startRecording(CallSession callSession) {
        logger.debug("Audio startRecording called.");
        // The main capture (FFmpeg and readers) is started by onStartCapture (video).
        // This method is part of the AudioDevice lifecycle. If onStartCapture hasn't been called,
        // audio won't flow yet.
        if (!isRunning) {
            logger.warn("Capture not started via onStartCapture (video). Audio recording will effectively begin when video capture starts.");
        }
        return 0;
    }

    @Override
    public int stopRecording(CallSession callSession) {
        logger.debug("Audio stopRecording called.");
        // This is part of the AudioDevice lifecycle.
        // The actual stopping of FFmpeg and threads is handled by onStopCapture (video).
        // We don't stop the entire capture here as video might still be needed.
        // If an audio-only stop is required, the overall stop logic needs refinement.
        // For now, this method is a no-op regarding process control, relying on onStopCapture.
        // We can clear the audio queue as a specific action for audio stopping.
        audioCacheQueue.clear();
        logger.debug("Audio cache cleared on stopRecording.");
        return 0;
    }

    @Override
    public void fetchRecordData(CallSession callSession, byte[] sampleData, int nSamples, int nSampleBytes, int nChannels, int nSampleRate, int nBuffSize) {
        VideoCaptureCapability currentVideoFormat = this.captureFormat; // Read volatile
        if (currentVideoFormat == null) {
            logger.warn("fetchRecordData: Video capture format is null. FFmpeg might not be started or video probing failed. Silencing audio.");
            fillWithSilence(sampleData, nBuffSize);
            return;
        }

        boolean restartDueToAudioMismatch = false;
        if (!ffmpegAudioInitialized || nSampleRate != this.currentFfmpegAudioSampleRate || nChannels != this.currentFfmpegAudioChannels) {
            logger.info("fetchRecordData: Audio parameters mismatch or FFmpeg audio not initialized. WebRTC wants: {}Hz {}ch. Current FFmpeg: {}Hz {}ch. Restarting FFmpeg.",
                nSampleRate, nChannels, this.currentFfmpegAudioSampleRate, this.currentFfmpegAudioChannels);

            if (!startFFmpegAndReaders(currentVideoFormat, nSampleRate, nChannels)) {
                logger.error("fetchRecordData: Failed to restart FFmpeg for new audio parameters. Audio will be silence.");
                fillWithSilence(sampleData, nBuffSize);
                return;
            }
            restartDueToAudioMismatch = true;
        }

        AudioCaptureParameters currentLocalAudioParams = this.audioCaptureParameters; // Read volatile
        boolean localParamsChangedOrRestarted = restartDueToAudioMismatch;
        if (!localParamsChangedOrRestarted && (currentLocalAudioParams == null ||
            currentLocalAudioParams.bufferSize != nBuffSize ||
            currentLocalAudioParams.sampleRate != nSampleRate ||
            currentLocalAudioParams.channels != nChannels)) {
            localParamsChangedOrRestarted = true; // Set true if local params (like just bufferSize) changed without full ffmpeg restart
        }

        if (localParamsChangedOrRestarted) {
            if (currentLocalAudioParams != null && !restartDueToAudioMismatch) {
                logger.info("fetchRecordData: Updating local audioCaptureParameters. New: SR={}, CH={}, BS={}. Old: SR={}, CH={}, BS={}",
                    nSampleRate, nChannels, nBuffSize,
                    currentLocalAudioParams.sampleRate, currentLocalAudioParams.channels, currentLocalAudioParams.bufferSize);
            } else if (!restartDueToAudioMismatch) {
                logger.info("fetchRecordData: Initializing local audioCaptureParameters. New: SR={}, CH={}, BS={}", nSampleRate, nChannels, nBuffSize);
            }
            // If restarted, audioCaptureParameters was already set by startFFmpegAndReaders with initial buffer size.
            // Here, we ensure it's updated with the exact nBuffSize from WebRTC.
            this.audioCaptureParameters = new AudioCaptureParameters(nSampleRate, nChannels, nBuffSize);

            if (!restartDueToAudioMismatch) { // If only local params changed (e.g. nBuffSize for same rate/ch), clear queue.
                // If FFmpeg restarted, startFFmpegAndReaders already cleared queues.
                logger.info("fetchRecordData: Audio parameters (esp. buffer size) changed locally, clearing audio cache queue (size before clear: {}).", audioCacheQueue.size());
                audioCacheQueue.clear();
            }
        }

        if (!isRunning && audioCacheQueue.isEmpty()) {
            fillWithSilence(sampleData, nBuffSize);
            return;
        }

        try {
            byte[] data = audioCacheQueue.poll(10, TimeUnit.MILLISECONDS);
            if (data != null) {
                if (data.length == nBuffSize) {
                    System.arraycopy(data, 0, sampleData, 0, nBuffSize);
                } else {
                    logger.warn("Audio data size error in fetch. Expected nBuffSize: {}, Got data.length: {}. Discarding and silencing.", nBuffSize, data.length);
                    fillWithSilence(sampleData, nBuffSize);
                }
            } else {
                fillWithSilence(sampleData, nBuffSize);
            }
        } catch (InterruptedException e) {
            logger.warn("Audio data fetch interrupted. Filling with silence.", e);
            Thread.currentThread().interrupt();
            fillWithSilence(sampleData, nBuffSize);
        }
    }

    private void fillWithSilence(byte[] buffer, int size) {
        for (int i = 0; i < size; i++) {
            buffer[i] = 0;
        }
    }

    @Override
    public void playoutData(CallSession callSession, String userId, byte[] sampleData, int nBuffSize) {
        // Not used for a capture-only device
    }

}