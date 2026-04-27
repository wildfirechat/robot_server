package cn.wildfirechat.app.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Mixes PCM audio from multiple participants into a single s16le stereo stream.
 * Output format: 48000 Hz, 2 channels, 16-bit signed little-endian, 20 ms frames.
 */
public class AudioMixer {
    private static final Logger LOG = LoggerFactory.getLogger(AudioMixer.class);

    // Output format constants
    private static final int SAMPLE_RATE    = 48000;
    private static final int CHANNELS       = 2;
    private static final int BYTES_PER_SAMPLE = 2;          // s16
    private static final int FRAME_MS       = 20;
    /** Samples per channel per frame: 48000 * 0.02 = 960 */
    private static final int FRAME_SAMPLES  = SAMPLE_RATE * FRAME_MS / 1000 * CHANNELS; // 1920
    /** Bytes per frame: 1920 * 2 = 3840 */
    private static final int FRAME_BYTES    = FRAME_SAMPLES * BYTES_PER_SAMPLE;          // 3840

    /** Per-user queue of raw PCM chunks as delivered by playoutData */
    private final Map<String, LinkedBlockingQueue<byte[]>> userQueues = new ConcurrentHashMap<>();
    /** Per-user leftover bytes that did not fill a complete frame in the last mix cycle */
    private final Map<String, byte[]> userLeftovers = new ConcurrentHashMap<>();

    private final String pipePath;
    private volatile boolean running = false;
    private Thread mixerThread;

    public AudioMixer(String pipePath) {
        this.pipePath = pipePath;
    }

    /** Called when a participant joins. Safe to call multiple times. */
    public void addParticipant(String userId) {
        userQueues.putIfAbsent(userId, new LinkedBlockingQueue<>(300));
        LOG.info("AudioMixer: added participant {}", userId);
    }

    /** Called when a participant leaves. */
    public void removeParticipant(String userId) {
        userQueues.remove(userId);
        userLeftovers.remove(userId);
        LOG.info("AudioMixer: removed participant {}", userId);
    }

    /**
     * Feed audio data received from a remote participant.
     * Called from the SDK's playoutData callback.
     */
    public void onAudioData(String userId, byte[] data, int size) {
        // Auto-register participant on first audio packet
        userQueues.computeIfAbsent(userId, k -> new LinkedBlockingQueue<>(300));

        LinkedBlockingQueue<byte[]> queue = userQueues.get(userId);
        if (queue == null || !running) return;

        byte[] copy = new byte[size];
        System.arraycopy(data, 0, copy, 0, size);

        if (!queue.offer(copy)) {
            // Queue full: drop oldest to make room
            queue.poll();
            queue.offer(copy);
        }
    }

    public void start() {
        running = true;
        mixerThread = new Thread(this::mixLoop, "hls-audio-mixer");
        mixerThread.setDaemon(true);
        mixerThread.start();
    }

    public void stop() {
        running = false;
        if (mixerThread != null) {
            mixerThread.interrupt();
        }
    }

    // -------------------------------------------------------------------------

    private void mixLoop() {
        final long frameIntervalNs = (long) FRAME_MS * 1_000_000L;
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

                byte[] frame = buildMixedFrame();
                out.write(frame);
                out.flush();
            }
        } catch (IOException e) {
            if (running) {
                LOG.error("AudioMixer: pipe write error on {}", pipePath, e);
            }
        }
        LOG.info("AudioMixer: loop ended");
    }

    private byte[] buildMixedFrame() {
        // Working buffer: int[] to accumulate samples without overflow before clamping
        int[] mixed = new int[FRAME_SAMPLES];

        for (Map.Entry<String, LinkedBlockingQueue<byte[]>> entry : userQueues.entrySet()) {
            String userId = entry.getKey();
            LinkedBlockingQueue<byte[]> queue = entry.getValue();

            // Drain all pending chunks into a contiguous byte array
            byte[] accum = userLeftovers.getOrDefault(userId, new byte[0]);

            List<byte[]> chunks = new ArrayList<>();
            queue.drainTo(chunks);
            if (!chunks.isEmpty()) {
                int extra = 0;
                for (byte[] c : chunks) extra += c.length;
                byte[] merged = new byte[accum.length + extra];
                System.arraycopy(accum, 0, merged, 0, accum.length);
                int pos = accum.length;
                for (byte[] c : chunks) {
                    System.arraycopy(c, 0, merged, pos, c.length);
                    pos += c.length;
                }
                accum = merged;
            }

            // Consume up to FRAME_BYTES from the front
            int useBytes = Math.min(accum.length, FRAME_BYTES);
            int useSamples = useBytes / BYTES_PER_SAMPLE;
            for (int i = 0; i < useSamples; i++) {
                // Little-endian s16
                int sample = (accum[i * 2] & 0xFF) | (accum[i * 2 + 1] << 8);
                mixed[i] += sample;
            }

            // Keep leftover bytes for the next frame
            if (accum.length > FRAME_BYTES) {
                byte[] leftover = new byte[accum.length - FRAME_BYTES];
                System.arraycopy(accum, FRAME_BYTES, leftover, 0, leftover.length);
                userLeftovers.put(userId, leftover);
            } else {
                userLeftovers.put(userId, new byte[0]);
            }
        }

        // Convert mixed int samples back to s16le bytes with clamping
        byte[] result = new byte[FRAME_BYTES];
        for (int i = 0; i < FRAME_SAMPLES; i++) {
            int clamped = Math.max(-32768, Math.min(32767, mixed[i]));
            result[i * 2]     = (byte) (clamped & 0xFF);
            result[i * 2 + 1] = (byte) ((clamped >> 8) & 0xFF);
        }
        return result;
    }
}
