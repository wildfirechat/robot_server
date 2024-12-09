package cn.wildfirechat.app.call;

import dev.onvoid.webrtc.media.FourCC;
import dev.onvoid.webrtc.media.video.I420Buffer;
import dev.onvoid.webrtc.media.video.VideoBufferConverter;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrackSink;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ImageVideoSink implements VideoTrackSink  {
    BlockingQueue<VideoFrame> frames = new LinkedBlockingQueue<>();
    boolean isRun = true;
    public final String userId;
    public final String callId;

    public ImageVideoSink(String userId, String callId) {
        this.userId = userId;
        this.callId = callId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                long time = System.currentTimeMillis();
                while (true) {
                    try {
                        VideoFrame frame = frames.poll(100, TimeUnit.MILLISECONDS);
                        if(frame != null) {
                            if(System.currentTimeMillis() - time > 3000) {
                                time = System.currentTimeMillis();
                                try {
                                    byte[] rgb = new byte[frame.buffer.getWidth()*frame.buffer.getHeight()*4];
                                    VideoBufferConverter.convertFromI420(frame.buffer, rgb, FourCC.ARGB);
                                    saveRGBAtoBMP(callId + frame.timestampNs + ".bmp", frame.buffer.getWidth(), frame.buffer.getHeight(), rgb);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            frame.release();
                        } else {
                            if(!isRun) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
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

            // 写入RGBA数据（注意BMP文件中的像素顺序为BGR）
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int index = (height - 1 - i) * width + j;
                    byte b = rgbaData[index * 4];
                    byte g = rgbaData[index * 4 + 1];
                    byte r = rgbaData[index * 4 + 2];
                    fos.write(b);
                    fos.write(g);
                    fos.write(r);
                    fos.write(rgbaData[index * 4 + 3]); // Alpha通道
                }
            }
        }
    }



    public void onCallEnded() {
        isRun = false;
    }

    @Override
    public void onVideoFrame(VideoFrame videoFrame) {
        videoFrame.retain();
        frames.add(videoFrame);
    }


}
