package cn.wildfirechat.app.call;

import cn.wildfirechat.AudioDevice;
import cn.wildfirechat.CallSession;
import dev.onvoid.webrtc.media.video.I420Buffer;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrackSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RtmpSink implements VideoTrackSink, AudioDevice {
    private static final Logger logger = LoggerFactory.getLogger(RtmpSink.class);

    private final String rtmpUrl;
    private final String hlsOutputPath;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // FFmpeg process for RTMP to HLS conversion
    private Process ffmpegProcess;

    // Named pipes for audio and video data
    private String videoPipePath;
    private String audioPipePath;

    // Audio parameters
    private volatile int audioSampleRate = 48000;
    private volatile int audioChannels = 2;
    private volatile boolean audioInitialized = false;

    // Video parameters
    private volatile int videoWidth = 640;
    private volatile int videoHeight = 480;
    private volatile int videoFrameRate = 30;
    private volatile boolean videoInitialized = false;

    // Thread executor for pipe writers
    private final ExecutorService writerExecutorService = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // Queues for buffering audio and video data
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(1024);
    private final BlockingQueue<VideoFrameData> videoQueue = new LinkedBlockingQueue<>(512);

    private static class VideoFrameData {
        final byte[] data;
        final int width;
        final int height;

        VideoFrameData(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }

    public RtmpSink(String rtmpUrl, String hlsOutputPath) {
        this.rtmpUrl = rtmpUrl;
        this.hlsOutputPath = hlsOutputPath;

        String pipeSuffix = UUID.randomUUID().toString().substring(0, 8);
        this.videoPipePath = "/tmp/rtmp_video_pipe_" + pipeSuffix;
        this.audioPipePath = "/tmp/rtmp_audio_pipe_" + pipeSuffix;
    }

    public RtmpSink(String rtmpUrl, String hlsOutputPath, int audioSampleRate, int audioChannels) {
        this(rtmpUrl, hlsOutputPath);
        this.audioSampleRate = audioSampleRate;
        this.audioChannels = audioChannels;
    }

    /**
     * Set audio parameters. This should be called before start() or will trigger FFmpeg restart if already running.
     */
    public synchronized void setAudioParameters(int sampleRate, int channels) {
        if (this.audioSampleRate != sampleRate || this.audioChannels != channels) {
            logger.info("Audio parameters changed: {}Hz {}ch -> {}Hz {}ch",
                this.audioSampleRate, this.audioChannels, sampleRate, channels);
            this.audioSampleRate = sampleRate;
            this.audioChannels = channels;

            if (isRunning.get()) {
                // Restart FFmpeg with new audio parameters
                restartFFmpegIfNeeded();
            }
        }
    }

    /**
     * Check if the RTMP sink is currently running
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Get current audio parameters
     */
    public String getAudioInfo() {
        return String.format("%dHz %dch", audioSampleRate, audioChannels);
    }

    /**
     * Get current video parameters
     */
    public String getVideoInfo() {
        return String.format("%dx%d@%dfps", videoWidth, videoHeight, videoFrameRate);
    }

    /**
     * Get queue statistics
     */
    public String getQueueStats() {
        return String.format("Audio queue: %d/%d, Video queue: %d/%d",
            audioQueue.size(), 100, videoQueue.size(), 30);
    }

    private synchronized boolean restartFFmpegIfNeeded() {
        if (!isRunning.get()) {
            return false;
        }

        logger.info("Restarting FFmpeg due to parameter changes");

        // Stop current FFmpeg process
        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            ffmpegProcess.destroy();
            try {
                if (!ffmpegProcess.waitFor(3, TimeUnit.SECONDS)) {
                    ffmpegProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                ffmpegProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        // Clear queues to avoid stale data
        audioQueue.clear();
        videoQueue.clear();

        // Restart FFmpeg with new parameters
        return startFFmpeg();
    }

    private boolean createNamedPipes() {
        try {
            // Delete any existing pipes first
            deleteNamedPipes();

            logger.debug("Creating named pipes: Video={}, Audio={}", videoPipePath, audioPipePath);
            Process pVideo = Runtime.getRuntime().exec("mkfifo -m 0666 " + videoPipePath);
            Process pAudio = Runtime.getRuntime().exec("mkfifo -m 0666 " + audioPipePath);

            if (pVideo.waitFor() != 0) {
                logger.error("Failed to create video pipe");
                return false;
            }
            if (pAudio.waitFor() != 0) {
                logger.error("Failed to create audio pipe");
                Files.deleteIfExists(Paths.get(videoPipePath));
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to create named pipes", e);
            return false;
        }
    }

    private void deleteNamedPipes() {
        try {
            Files.deleteIfExists(Paths.get(videoPipePath));
            Files.deleteIfExists(Paths.get(audioPipePath));
        } catch (IOException e) {
            logger.warn("Failed to delete named pipes", e);
        }
    }

    public synchronized boolean start() {
        if (isRunning.get()) {
            logger.warn("RtmpSink already running");
            return true;
        }

        logger.info("Starting RtmpSink with RTMP URL: {}, HLS output: {}", rtmpUrl, hlsOutputPath);

        if (!createNamedPipes()) {
            logger.error("Failed to create named pipes");
            return false;
        }

        // You're getting stuck at opening the pipe. Opening a fifo for writing will block until someone opens it for reading.
        startPipeWriters();

//        try {
//            // wait for a moment to ensure pipes are ready
//            Thread.sleep(30 * 1000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
        if (!startFFmpeg()) {
            logger.error("Failed to start FFmpeg");
            deleteNamedPipes();
            return false;
        }

        isRunning.set(true);

        logger.info("RtmpSink started successfully");
        return true;
    }

    public synchronized void stop() {
        if (!isRunning.get()) {
            logger.debug("RtmpSink already stopped");
            return;
        }

        logger.info("Stopping RtmpSink");
        isRunning.set(false);

        // Stop FFmpeg process
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
            ffmpegProcess = null;
        }

        // Clear queues
        audioQueue.clear();
        videoQueue.clear();

        // Shutdown executor service
        writerExecutorService.shutdown();
        try {
            if (!writerExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                writerExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clean up pipes
        deleteNamedPipes();

        logger.info("RtmpSink stopped");
    }

    private boolean startFFmpeg() {
        try {
            // Create HLS output directory if it doesn't exist
            Files.createDirectories(Paths.get(hlsOutputPath).getParent());

            String ffmpegCommand = String.format(
                "/opt/homebrew/bin/ffmpeg -y -nostdin " +
                    // 临时屏蔽视频
//                "-f rawvideo -pix_fmt yuv420p -s %dx%d -r %d -i %s " +
                    "-f s16le -ar %d -ac %d -i %s " +
                    "-c:v libx264 -preset ultrafast -tune zerolatency -crf 23 " +
                    "-c:a aac -b:a 128k " +
                    "-f flv %s " +
                    "-c:v libx264 -preset ultrafast -tune zerolatency -crf 23 " +
                    "-c:a aac -b:a 128k " +
                    "-f hls -hls_time 6 -hls_list_size 10 -hls_flags delete_segments %s",
                // 临时屏蔽视频
//                videoWidth, videoHeight, videoFrameRate, videoPipePath,
                audioSampleRate, audioChannels, audioPipePath,
                rtmpUrl,
                hlsOutputPath
            );

            logger.info("Starting FFmpeg with command: {}", ffmpegCommand);

            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
            processBuilder.redirectErrorStream(true);
            ffmpegProcess = processBuilder.start();

            // Start a thread to consume FFmpeg output
            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(ffmpegProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && isRunning.get()) {
                        logger.debug("FFmpeg: {}", line);
                    }
                } catch (IOException e) {
                    if (isRunning.get()) {
                        logger.error("Error reading FFmpeg output", e);
                    }
                }
            }, "ffmpeg-output-reader").start();

            return true;
        } catch (Exception e) {
            logger.error("Failed to start FFmpeg", e);
            return false;
        }
    }

    private void startPipeWriters() {
        // Video pipe writer
        writerExecutorService.submit(() -> {
            Thread.currentThread().setName("rtmp-video-writer");
            logger.debug("Video pipe writer started");

            try (FileOutputStream videoOut = new FileOutputStream(videoPipePath)) {
                while (isRunning.get()) {
                    try {
                        VideoFrameData frameData = videoQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (frameData != null) {
                            videoOut.write(frameData.data);
                            videoOut.flush();
                        }
                    } catch (InterruptedException e) {
                        if (isRunning.get()) {
                            logger.warn("Video writer interrupted");
                        }
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        if (isRunning.get()) {
                            logger.error("Error writing video data to pipe", e);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    logger.error("Failed to open video pipe for writing", e);
                }
            }

            logger.debug("Video pipe writer stopped");
        });

        // Audio pipe writer
        writerExecutorService.submit(() -> {
            Thread.currentThread().setName("rtmp-audio-writer");
            logger.debug("Audio pipe writer started");

            try (
                // 方法会 block，直到打开管道的另一端，也就是读的那段
                FileOutputStream audioOut = new FileOutputStream(audioPipePath)) {
                while (isRunning.get()) {
                    try {
                        byte[] audioData = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (audioData != null) {
                            logger.debug("Audio pipe writer {}", audioData.length);
                            audioOut.write(audioData);
                            audioOut.flush();
                        }
                    } catch (InterruptedException e) {
                        if (isRunning.get()) {
                            logger.warn("Audio writer interrupted");
                        }
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        if (isRunning.get()) {
                            logger.error("Error writing audio data to pipe", e);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    logger.error("Failed to open audio pipe for writing", e);
                }
            }

            logger.debug("Audio pipe writer stopped");
        });
    }

    private byte[] convertVideoFrameToYUV420(VideoFrame videoFrame) {
        try {
            I420Buffer i420Buffer = videoFrame.buffer.toI420();

            int width = i420Buffer.getWidth();
            int height = i420Buffer.getHeight();

            // Update video parameters if they changed
            if (this.videoWidth != width || this.videoHeight != height) {
                logger.info("Video dimensions changed from {}x{} to {}x{}",
                    this.videoWidth, this.videoHeight, width, height);
                this.videoWidth = width;
                this.videoHeight = height;
                this.videoInitialized = true;
            }

            ByteBuffer yPlane = i420Buffer.getDataY();
            ByteBuffer uPlane = i420Buffer.getDataU();
            ByteBuffer vPlane = i420Buffer.getDataV();

            int ySize = yPlane.remaining();
            int uSize = uPlane.remaining();
            int vSize = vPlane.remaining();

            byte[] yuv420Data = new byte[ySize + uSize + vSize];

            yPlane.get(yuv420Data, 0, ySize);
            uPlane.get(yuv420Data, ySize, uSize);
            vPlane.get(yuv420Data, ySize + uSize, vSize);

            return yuv420Data;
        } catch (Exception e) {
            logger.error("Failed to convert video frame to YUV420", e);
            return null;
        }
    }

    @Override
    public int initPlayout(CallSession callSession, String userId) {
        logger.debug("Audio playout initialized");
        if (!audioInitialized) {
            logger.info("Audio playout initialized with sample rate: {}Hz, channels: {}",
                audioSampleRate, audioChannels);
            audioInitialized = true;
        }
        return 0;
    }

    @Override
    public int stopPlayout(CallSession callSession, String s) {
        return 0;
    }

    @Override
    public int initRecording(CallSession callSession) {
        logger.debug("Audio recording initialized");
        return 0;
    }

    @Override
    public int startRecording(CallSession callSession) {
        logger.debug("Audio recording started");
        return 0;
    }

    @Override
    public int stopRecording(CallSession callSession) {
        logger.debug("Audio recording stopped");
        return 0;
    }

    @Override
    public void fetchRecordData(CallSession callSession, byte[] sampleData, int nSamples, int nSampleBytes, int nChannels, int nSampleRate, int nBuffSize) {
        // This method is used for recording local microphone audio
        // For RTMP streaming, we typically don't need to stream local microphone audio
        // We're interested in streaming the received audio from remote participants
        logger.debug("Recording local audio data: {} bytes, {}Hz {}ch", nBuffSize, nSampleRate, nChannels);
        // Not processing local microphone audio for streaming
    }

    @Override
    public void playoutData(CallSession callSession, String userId, byte[] sampleData, int nBuffSize) {
        // This method receives audio data from remote participants that we want to stream
        logger.debug("jyj playoutData");
        if (!isRunning.get()) {
            return;
        }

        logger.debug("Received remote audio data from user {}: {} bytes", userId, nBuffSize);

        // For RTMP streaming, we want to stream the received audio data
        byte[] audioData = new byte[nBuffSize];
        System.arraycopy(sampleData, 0, audioData, 0, nBuffSize);

        if (!audioQueue.offer(audioData)) {
            logger.warn("Audio queue full, dropping remote audio data");
        }
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame) {
        logger.debug("jyj onVideoFrame");
        if (!isRunning.get()) {
            return;
        }

        byte[] yuvData = convertVideoFrameToYUV420(videoFrame);
        if (yuvData != null) {
            VideoFrameData frameData = new VideoFrameData(yuvData, videoWidth, videoHeight);
            if (!videoQueue.offer(frameData)) {
                logger.warn("Video queue full, dropping video frame");
            }
        }
    }
}
