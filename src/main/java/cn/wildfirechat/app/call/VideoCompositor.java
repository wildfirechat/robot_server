package cn.wildfirechat.app.call;

import dev.onvoid.webrtc.media.video.I420Buffer;
import dev.onvoid.webrtc.media.video.VideoFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composes YUV420p frames from multiple participants into a single grid frame.
 *
 * Grid layout (cols × rows):
 *   1 participant  → 1×1 (full screen)
 *   2 participants → 2×1 (side by side)
 *   3-4            → 2×2
 *   5-6            → 3×2
 *   7-9            → 3×3
 *
 * Each participant's frame is letterboxed (preserving aspect ratio) into its cell
 * using nearest-neighbour scaling. The background is black (Y=0, U=V=128).
 */
public class VideoCompositor {
    private static final Logger LOG = LoggerFactory.getLogger(VideoCompositor.class);

    private static final class YuvFrame {
        final byte[] data;   // planar YUV420p: Y then U then V
        final int width;
        final int height;

        YuvFrame(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }

    /** Stable ordered list of participant IDs (determines grid slot assignment). */
    private final List<String> participantOrder = Collections.synchronizedList(new ArrayList<>());
    /** Latest decoded frame per participant. */
    private final Map<String, YuvFrame> latestFrames = new ConcurrentHashMap<>();

    private final String pipePath;
    private final int outputWidth;
    private final int outputHeight;
    private final int outputFps;

    private volatile boolean running = false;
    private Thread compositorThread;

    public VideoCompositor(String pipePath, int outputWidth, int outputHeight, int outputFps) {
        this.pipePath = pipePath;
        // Ensure even dimensions (required for YUV420p)
        this.outputWidth  = outputWidth  & ~1;
        this.outputHeight = outputHeight & ~1;
        this.outputFps    = outputFps;
    }

    public void addParticipant(String userId) {
        synchronized (participantOrder) {
            if (!participantOrder.contains(userId)) {
                participantOrder.add(userId);
            }
        }
        LOG.info("VideoCompositor: added participant {}", userId);
    }

    public void removeParticipant(String userId) {
        synchronized (participantOrder) {
            participantOrder.remove(userId);
        }
        latestFrames.remove(userId);
        LOG.info("VideoCompositor: removed participant {}", userId);
    }

    /** Called from VideoTrackSink.onVideoFrame – stores the latest frame for this user. */
    public void updateFrame(String userId, VideoFrame frame) {
        I420Buffer i420 = null;
        try {
            i420 = frame.buffer.toI420();
            int w = i420.getWidth();
            int h = i420.getHeight();

            ByteBuffer yBuf = i420.getDataY();
            ByteBuffer uBuf = i420.getDataU();
            ByteBuffer vBuf = i420.getDataV();

            int yLen = yBuf.remaining();
            int uLen = uBuf.remaining();
            int vLen = vBuf.remaining();

            byte[] yuv = new byte[yLen + uLen + vLen];
            yBuf.get(yuv, 0,        yLen);
            uBuf.get(yuv, yLen,     uLen);
            vBuf.get(yuv, yLen + uLen, vLen);

            latestFrames.put(userId, new YuvFrame(yuv, w, h));
        } catch (Exception e) {
            LOG.warn("VideoCompositor: failed to extract YUV from user {}", userId, e);
        } finally {
            // Must always release the I420Buffer to decrement WebRTC's native reference count.
            // Failing to do so exhausts the native buffer pool and causes
            // "No decodable frame in 200 ms" / keyframe-request loops.
            if (i420 != null) {
                i420.release();
            }
        }
    }

    public void start() {
        running = true;
        compositorThread = new Thread(this::compositeLoop, "hls-video-compositor");
        compositorThread.setDaemon(true);
        compositorThread.start();
    }

    public void stop() {
        running = false;
        if (compositorThread != null) {
            compositorThread.interrupt();
        }
    }

    // -------------------------------------------------------------------------

    private void compositeLoop() {
        final long frameIntervalNs = 1_000_000_000L / outputFps;
        try (FileOutputStream out = new FileOutputStream(pipePath)) {
            long nextFrameTime = System.nanoTime();
            while (running) {
                long sleepNs = nextFrameTime - System.nanoTime();
                if (sleepNs > 100_000L) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L, (int) (sleepNs % 1_000_000L));
                    } catch (InterruptedException e) {
                        if (!running) break;
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                nextFrameTime += frameIntervalNs;

                byte[] frame = buildCompositeFrame();
                out.write(frame);
                out.flush();
            }
        } catch (IOException e) {
            if (running) {
                LOG.error("VideoCompositor: pipe write error on {}", pipePath, e);
            }
        }
        LOG.info("VideoCompositor: loop ended");
    }

    private byte[] buildCompositeFrame() {
        List<String> participants;
        synchronized (participantOrder) {
            participants = new ArrayList<>(participantOrder);
        }

        int ySize  = outputWidth * outputHeight;
        int uvSize = (outputWidth / 2) * (outputHeight / 2);
        byte[] output = new byte[ySize + uvSize * 2];

        // Black background: Y=0 already; fill U and V with 128 (neutral chroma)
        Arrays.fill(output, ySize, ySize + uvSize * 2, (byte) 128);

        if (participants.isEmpty()) {
            return output; // all-black frame
        }

        int[] grid = gridFor(participants.size());
        int cols = grid[0];
        int rows = grid[1];
        int cellW = (outputWidth  / cols) & ~1;
        int cellH = (outputHeight / rows) & ~1;

        for (int i = 0; i < participants.size(); i++) {
            String userId = participants.get(i);
            YuvFrame src = latestFrames.get(userId);
            if (src == null) continue; // no frame yet → leave cell black

            int col = i % cols;
            int row = i / cols;
            int dstX = col * cellW;
            int dstY = row * cellH;

            renderIntoCell(src, output, dstX, dstY, cellW, cellH);
        }

        return output;
    }

    /**
     * Nearest-neighbour scale + letterbox src into the destination cell.
     * Cell area not covered by the source image remains black (already initialised).
     */
    private void renderIntoCell(YuvFrame src, byte[] dst, int dstX, int dstY, int cellW, int cellH) {
        // Compute letterbox dimensions preserving source aspect ratio
        int drawW, drawH;
        if ((long) src.width * cellH > (long) src.height * cellW) {
            drawW = cellW;
            drawH = src.height * cellW / src.width;
        } else {
            drawH = cellH;
            drawW = src.width * cellH / src.height;
        }
        // Must be even for YUV420p
        drawW = drawW & ~1;
        drawH = drawH & ~1;

        // Centre within the cell (also aligned to even)
        int offX = (dstX + (cellW - drawW) / 2) & ~1;
        int offY = (dstY + (cellH - drawH) / 2) & ~1;

        int srcYSize  = src.width * src.height;
        int srcUVSize = (src.width / 2) * (src.height / 2);

        int dstYSize  = outputWidth * outputHeight;
        int dstUVSize = (outputWidth / 2) * (outputHeight / 2);

        int outW2 = outputWidth  / 2;
        int srcW2 = src.width    / 2;
        int srcH2 = src.height   / 2;

        // --- Y plane (full resolution) ---
        for (int y = 0; y < drawH; y++) {
            int srcY = y * src.height / drawH;
            int dstRow = (offY + y) * outputWidth + offX;
            int srcRow = srcY * src.width;
            for (int x = 0; x < drawW; x++) {
                int srcX = x * src.width / drawW;
                dst[dstRow + x] = src.data[srcRow + srcX];
            }
        }

        // --- U plane (half resolution) ---
        int drawW2 = drawW / 2;
        int drawH2 = drawH / 2;
        int offX2  = offX  / 2;
        int offY2  = offY  / 2;

        for (int y = 0; y < drawH2; y++) {
            int srcY = y * srcH2 / drawH2;
            int dstRow = dstYSize + (offY2 + y) * outW2 + offX2;
            int srcRow = srcYSize + srcY * srcW2;
            for (int x = 0; x < drawW2; x++) {
                int srcX = x * srcW2 / drawW2;
                dst[dstRow + x] = src.data[srcRow + srcX];
            }
        }

        // --- V plane (half resolution) ---
        int dstVOff = dstYSize + dstUVSize;
        int srcVOff = srcYSize + srcUVSize;

        for (int y = 0; y < drawH2; y++) {
            int srcY = y * srcH2 / drawH2;
            int dstRow = dstVOff + (offY2 + y) * outW2 + offX2;
            int srcRow = srcVOff + srcY * srcW2;
            for (int x = 0; x < drawW2; x++) {
                int srcX = x * srcW2 / drawW2;
                dst[dstRow + x] = src.data[srcRow + srcX];
            }
        }
    }

    /** Returns [cols, rows] for a grid that fits n participants. */
    private static int[] gridFor(int n) {
        if (n <= 1) return new int[]{1, 1};
        if (n <= 2) return new int[]{2, 1};
        if (n <= 4) return new int[]{2, 2};
        if (n <= 6) return new int[]{3, 2};
        return new int[]{3, 3};
    }
}
