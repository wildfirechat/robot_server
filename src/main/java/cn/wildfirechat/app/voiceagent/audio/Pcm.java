package cn.wildfirechat.app.voiceagent.audio;

/** int16 小端 PCM 的字节/采样互转、下混和能量计算。 */
public final class Pcm {
    private Pcm() {
    }

    /** 小端 int16 字节流转单声道采样，多声道取平均。 */
    public static short[] toMono(byte[] data, int len, int channels) {
        int frames = len / 2 / channels;
        short[] out = new short[frames];
        int p = 0;
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += (short) ((data[p] & 0xff) | (data[p + 1] << 8));
                p += 2;
            }
            out[i] = (short) (sum / channels);
        }
        return out;
    }

    /** 单声道采样转小端 int16 字节流，需要时复制到多声道。 */
    public static byte[] fromMono(short[] samples, int channels) {
        byte[] out = new byte[samples.length * 2 * channels];
        int p = 0;
        for (short s : samples) {
            for (int c = 0; c < channels; c++) {
                out[p++] = (byte) (s & 0xff);
                out[p++] = (byte) ((s >> 8) & 0xff);
            }
        }
        return out;
    }

    /** 小端 int16 字节流转单声道采样。 */
    public static short[] leToShorts(byte[] data, int off, int len) {
        int n = len / 2;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) ((data[off + i * 2] & 0xff) | (data[off + i * 2 + 1] << 8));
        }
        return out;
    }

    /** 帧的均方根，用来判断这一帧有没有人在说话。 */
    public static double rms(short[] samples) {
        if (samples.length == 0) {
            return 0;
        }
        double sum = 0;
        for (short s : samples) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / samples.length);
    }
}
