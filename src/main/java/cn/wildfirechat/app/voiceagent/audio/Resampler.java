package cn.wildfirechat.app.voiceagent.audio;

import java.util.Arrays;

/**
 * 单声道线性插值重采样，跨批保持相位。
 *
 * 通话音频是每 10ms 一帧连续送进来的，如果每帧独立重采样，帧与帧的接缝处相位会跳变，
 * 结果就是每 10ms 一个爆音。所以这里把上一批的最后一个采样留下来当作本批的 -1 号采样，
 * 并且把小数位置 pos 折算到下一批的坐标系里带过去。
 */
public class Resampler {
    private static final short[] EMPTY = new short[0];

    private final int srcRate;
    private final int dstRate;
    private final double step;

    private double pos;
    private short last;
    private boolean hasLast;

    public Resampler(int srcRate, int dstRate) {
        this.srcRate = srcRate;
        this.dstRate = dstRate;
        this.step = (double) srcRate / dstRate;
    }

    public int getSrcRate() {
        return srcRate;
    }

    public int getDstRate() {
        return dstRate;
    }

    public short[] process(short[] in) {
        if (in == null || in.length == 0) {
            return EMPTY;
        }
        if (srcRate == dstRate) {
            return in;
        }

        // buf[0] 是上一批的尾采样，buf[1..n] 是本批，这样 pos 可以从上批的尾部继续插值
        short[] buf = new short[in.length + 1];
        buf[0] = hasLast ? last : in[0];
        System.arraycopy(in, 0, buf, 1, in.length);

        int limit = buf.length - 1;
        int estimate = (int) Math.ceil((limit - pos) / step) + 2;
        if (estimate < 0) {
            estimate = 0;
        }
        short[] tmp = new short[estimate];
        int n = 0;
        while (pos < limit && n < tmp.length) {
            int i = (int) pos;
            double frac = pos - i;
            double v = buf[i] * (1 - frac) + buf[i + 1] * frac;
            tmp[n++] = clamp(v);
            pos += step;
        }
        // 把位置折算到下一批的坐标系：本批的最后一个采样将成为下一批的 0 号
        pos -= limit;
        if (pos < 0) {
            pos = 0;
        }
        last = in[in.length - 1];
        hasLast = true;
        return n == tmp.length ? tmp : Arrays.copyOf(tmp, n);
    }

    private static short clamp(double v) {
        long r = Math.round(v);
        if (r > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (r < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) r;
    }
}
