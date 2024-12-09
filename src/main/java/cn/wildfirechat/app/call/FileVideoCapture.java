package cn.wildfirechat.app.call;

import cn.wildfirechat.pojos.Conversation;
import dev.onvoid.webrtc.media.video.JavaVideoCapture;
import dev.onvoid.webrtc.media.video.VideoCaptureCapability;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.opencv.core.CvType.CV_8UC3;

public class FileVideoCapture implements JavaVideoCapture {
    private static final Logger LOG = LoggerFactory.getLogger(FileVideoCapture.class);
    private BlockingQueue<byte[]> cacheQueue = new LinkedBlockingQueue<>();
    private final String videoFilePath;
    private final Conversation conversation;
    private final String callId;
    private FFmpegFrameGrabber grabber;
    private Thread readThread;
    private boolean isStoped;

    private static int videoWidth;
    private static int videoHeight;
    private static final int FPT = 15;

    public FileVideoCapture(String videoFilePath, Conversation conversation, String callId) {
        this.videoFilePath = videoFilePath;
        this.conversation = conversation;
        this.callId = callId;
    }

    public VideoCaptureCapability getCapability() {
        if(videoWidth == 0) {
            try {
                FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new File(videoFilePath));
                grabber.start();
                videoWidth = grabber.getImageWidth();
                videoHeight = grabber.getImageHeight();
                grabber.stop();
                grabber.release();
            } catch (FFmpegFrameGrabber.Exception e) {
                e.printStackTrace();
            }
        }
        VideoCaptureCapability captureCapability = new VideoCaptureCapability(videoWidth, videoHeight, FPT);
        return captureCapability;
    }

    @Override
    public int onInit(String s) {
        return 0;
    }

    @Override
    public int onStartCapture(int i, int i1, int i2) {
        try {
            startReadFramesFromFile();
        } catch (FFmpegFrameGrabber.Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public int onStopCapture() {
        System.out.println("FileVideoCapture onStopCapture");
        stopReadFrames();
        return 0;
    }

    @Override
    public int onFetchFrame(byte[] bytes, int i, int i1, int i2, int i3) {
        try {
            byte[] data = cacheQueue.poll(10, TimeUnit.MILLISECONDS);
            if(data != null) {
                if (data.length == bytes.length) {
                    System.arraycopy(data, 0, bytes, 0, bytes.length);
                } else {
                    System.out.println("data size error");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    synchronized private void stopReadFrames() {
        isStoped = true;
    }

    private void startReadFramesFromFile() throws FFmpegFrameGrabber.Exception {
        isStoped = false;
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                grabber = new FFmpegFrameGrabber(new File(videoFilePath));
                try {
                    grabber.start();
                    // Initialize frame converter for converting Frame to BufferedImage
                    Java2DFrameConverter converter = new Java2DFrameConverter();

                    long startTime = System.currentTimeMillis();
                    // Loop through video frames
                    Frame frame;
                    while (!isStoped) {
                        if((frame = grabber.grab()) == null) {
                            LOG.info("Restart video grabber({})!!!", callId);
                            grabber.restart();
                            startTime = System.currentTimeMillis();
                            frame = grabber.grab();
                        }
                        if(frame.type == Frame.Type.AUDIO) {
                            continue;
                        }
                        // Convert Frame to BufferedImage
                        long now = System.currentTimeMillis() - startTime;
                        long videoTime = frame.timestamp/1000;

                        if(now - videoTime > 100) {
                            continue;
                        }

                        //时间同步
                        while (videoTime > now && !isStoped) {
                            Thread.sleep(10);
                            now = System.currentTimeMillis() - startTime;
                        }

                        BufferedImage bufferedImage = converter.convert(frame);

                        // Convert BufferedImage to I420 format (YUV420)
                        byte[] i420Data = convertToI420(bufferedImage);
                        cacheQueue.add(i420Data);
                    }

                    grabber.stop();
                    grabber.release();
                    grabber = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        readThread.start();
    }

    private byte[] convertToI420(BufferedImage image) {
        byte[] rgbData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        Mat rgbMat = new Mat(videoHeight, videoWidth, CV_8UC3, new BytePointer(rgbData));

        // 创建YUV420格式的Mat对象
        Mat yuvImage = new Mat();

        // 将RGB图像转换为YUV420格式
        opencv_imgproc.cvtColor(rgbMat, yuvImage, opencv_imgproc.COLOR_BGR2YUV_I420);

        // 获取YUV420格式的二进制数组
        BytePointer data = yuvImage.data();
        byte[] yuv420 = new byte[(int) (yuvImage.total() * yuvImage.elemSize())];
        data.get(yuv420);

        rgbMat.release();
        yuvImage.release();
        return yuv420;
    }

}
