package cn.wildfirechat.app.voiceagent.audio;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/**
 * 待播放的 AI 语音队列。
 *
 * TTS 出来的音频块跟通话的 10ms 帧不对齐，所以这里按字节读写，read 每次精确取走
 * 一帧的长度，不够的部分补静音。flush 用于打断：用户一开口就把还没播的全丢掉。
 *
 * 容量按通话格式实时算（见 setCapacityBytes）。队列满的时候<b>阻塞写入方</b>而不是
 * 丢队头：TTS 比实时快，一段稍长的回答很容易堆满队列，丢队头等于把正在播的句子
 * 从中间掐掉，听感上就是无缘无故少了几个字——文本对、合成也对，就是丢音。
 * 写入方是 TTS 那个单线程，让它等一等没有代价；被打断时 flush 会把它叫醒。
 */
public class PlaybackQueue {
    private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
    private int capacity;
    private int headOffset;
    private int totalBytes;
    private int waiters;
    /** flush 一次加一，用来叫醒正在等空位的写入方，并告诉它这块音频已经过期 */
    private long generation;
    private boolean closed;

    public PlaybackQueue(int capacityBytes) {
        this.capacity = Math.max(1, capacityBytes);
    }

    /**
     * 通话格式确定后重算上限。容量必须跟采样率、声道数挂钩：同样是 640000 字节，
     * 16k 单声道能存 20 秒，48k 立体声只有 3.3 秒。
     */
    public synchronized void setCapacityBytes(int bytes) {
        int c = Math.max(1, bytes);
        if (c != capacity) {
            capacity = c;
            notifyAll();
        }
    }

    public synchronized int getCapacityBytes() {
        return capacity;
    }

    public boolean offer(byte[] pcm) {
        return offer(pcm, null);
    }

    /**
     * 排进播放队列，队列满就等播放腾出空位。
     *
     * @param alive 每次醒来检查一次，返回 false（本轮已被打断）就放弃这块音频
     * @return true 表示已入队；false 表示这块音频被放弃（打断、关闭或线程被中断）
     */
    public boolean offer(byte[] pcm, BooleanSupplier alive) {
        if (pcm == null || pcm.length == 0) {
            return false;
        }
        synchronized (this) {
            // totalBytes > 0 是防死锁：单块比整个容量还大时，至少让它进得去
            while (!closed && totalBytes > 0 && totalBytes + pcm.length > capacity) {
                if (alive != null && !alive.getAsBoolean()) {
                    return false;
                }
                long gen = generation;
                waiters++;
                try {
                    wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } finally {
                    waiters--;
                }
                if (gen != generation) {
                    return false; // 中途被清空过，这块属于上一轮，别再播了
                }
            }
            if (closed) {
                return false;
            }
            chunks.addLast(pcm);
            totalBytes += pcm.length;
            return true;
        }
    }

    /** 取走 len 字节，不足补 0。返回实际取到的有效字节数。 */
    public synchronized int read(byte[] dst, int len) {
        int written = 0;
        while (written < len && !chunks.isEmpty()) {
            byte[] head = chunks.peekFirst();
            int avail = head.length - headOffset;
            int take = Math.min(avail, len - written);
            System.arraycopy(head, headOffset, dst, written, take);
            written += take;
            headOffset += take;
            totalBytes -= take;
            if (headOffset >= head.length) {
                chunks.pollFirst();
                headOffset = 0;
            }
        }
        for (int i = written; i < len; i++) {
            dst[i] = 0;
        }
        if (written > 0 && waiters > 0) {
            notifyAll();
        }
        return written;
    }

    public synchronized void flush() {
        chunks.clear();
        headOffset = 0;
        totalBytes = 0;
        generation++;
        notifyAll();
    }

    /** 通话结束：叫醒还在等空位的 TTS 线程，别让它一直挂在那里 */
    public synchronized void close() {
        closed = true;
        chunks.clear();
        headOffset = 0;
        totalBytes = 0;
        generation++;
        notifyAll();
    }

    public synchronized boolean isPlaying() {
        return totalBytes > 0;
    }

    public synchronized int pendingBytes() {
        return totalBytes;
    }
}
