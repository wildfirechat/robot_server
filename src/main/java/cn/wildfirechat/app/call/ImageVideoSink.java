package cn.wildfirechat.app.call;

import dev.onvoid.webrtc.media.FourCC;
import dev.onvoid.webrtc.media.video.I420Buffer;
import dev.onvoid.webrtc.media.video.VideoBufferConverter;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrackSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ImageVideoSink implements VideoTrackSink  {
    private static final Logger LOG = LoggerFactory.getLogger(ImageVideoSink.class);
    // Bounded queue: when full, frames are dropped (and released) instead of piling up native buffers
    private static final int MAX_QUEUE_SIZE = 30;
    // Thread-safe formatter for snapshot file names
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    BlockingQueue<VideoFrame> frames = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    volatile boolean isRun = true;
    public final String userId;
    public final String callId;
    private long receivedFrames = 0;

    public ImageVideoSink(String userId, String callId) {
        this.userId = userId;
        this.callId = callId;
        LOG.info("ImageVideoSink created, userId={}, callId={}", userId, callId);
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                long time = System.currentTimeMillis();
                while (true) {
                    try {
                        VideoFrame frame = frames.poll(100, TimeUnit.MILLISECONDS);
                        if(frame != null) {
                            receivedFrames++;
                            if(receivedFrames == 1 || receivedFrames % 300 == 0) {
                                LOG.info("ImageVideoSink {}/{} received {} frames", userId, callId, receivedFrames);
                            }
                            if(System.currentTimeMillis() - time > 3000) {
                                time = System.currentTimeMillis();
                                try {
                                    byte[] rgb = new byte[frame.buffer.getWidth()*frame.buffer.getHeight()*4];
                                    VideoBufferConverter.convertFromI420(frame.buffer, rgb, FourCC.ARGB);
                                    String filename = sanitize(callId) + "_" + sanitize(userId) + "_" + FILE_TIME_FORMAT.format(LocalDateTime.now()) + ".bmp";
                                    saveRGBAtoBMP(filename, frame.buffer.getWidth(), frame.buffer.getHeight(), rgb);
                                    LOG.info("ImageVideoSink saved snapshot: {}", new File(filename).getAbsolutePath());
                                } catch (Exception e) {
                                    LOG.error("ImageVideoSink failed to save snapshot, userId={}, callId={}", userId, callId, e);
                                }
                            }
                            frame.release();
                        } else {
                            if(!isRun) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        LOG.warn("ImageVideoSink worker interrupted", e);
                    }
                }
                LOG.info("ImageVideoSink worker exited, userId={}, callId={}, totalFrames={}", userId, callId, receivedFrames);
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    // Keep only filename-safe characters
    private static String sanitize(String s) {
        if (s == null) {
            return "null";
        }
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
    public static void saveRGBAtoBMP(String filename, int width, int height, byte[] rgbaData) throws IOException {
        int bmpFileSize = 54 + rgbaData.length; // BMP文件大小
        byte[] bmpHeader = {
                'B', 'M', // 文件类型
                (byte) (bmpFileSize), (byte) (bmpFileSize >> 8), (byte) (bmpFileSize >> 16), (byte) (bmpFileSize >> 24), // 文件大小
                0, 0, 0, 0, // 保留字段
                54, 0, 0, 0 // 数据偏移量
        };

        int dibHeaderSize = 40; // 位图信息头大小
        byte[] dibHeader = {
                (byte) (dibHeaderSize), 0, 0, 0, // 信息头大小
                (byte) (width), (byte) (width >> 8), (byte) (width >> 16), (byte) (width >> 24), // 图像宽度
                (byte) (height), (byte) (height >> 8), (byte) (height >> 16), (byte) (height >> 24), // 图像高度
                1, 0, // 颜色平面数
                32, 0, // 每像素位数（32位）
                0, 0, 0, 0, // 压缩类型（无压缩）
                (byte) (rgbaData.length), (byte) (rgbaData.length >> 8), (byte) (rgbaData.length >> 16), (byte) (rgbaData.length >> 24), // 图像数据大小
                13, 0, 0, 0, // 水平分辨率（像素/米）
                13, 0, 0, 0, // 垂直分辨率（像素/米）
                0, 0, 0, 0, // 使用的颜色数
                0, 0, 0, 0 // 重要颜色数
        };

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            // 写入BMP文件头和位图信息头
            fos.write(bmpHeader);
            fos.write(dibHeader);

            // 写入RGBA数据（注意BMP文件中的像素顺序为BGR），先整理到缓冲再一次写入
            byte[] pixelData = new byte[rgbaData.length];
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int srcIndex = ((height - 1 - i) * width + j) * 4;
                    int dstIndex = (i * width + j) * 4;
                    pixelData[dstIndex]     = rgbaData[srcIndex];     // B
                    pixelData[dstIndex + 1] = rgbaData[srcIndex + 1]; // G
                    pixelData[dstIndex + 2] = rgbaData[srcIndex + 2]; // R
                    pixelData[dstIndex + 3] = rgbaData[srcIndex + 3]; // Alpha通道
                }
            }
            fos.write(pixelData);
        }
    }



    public void onCallEnded() {
        isRun = false;
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame) {
        if(!isRun) {
            return;
        }
        videoFrame.retain();
        if(!frames.offer(videoFrame)) {
            // Queue full: drop the frame and release it, otherwise the retained
            // native buffer would leak off-heap
            videoFrame.release();
        }
    }


}
