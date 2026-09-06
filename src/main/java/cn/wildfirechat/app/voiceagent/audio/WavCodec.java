package cn.wildfirechat.app.voiceagent.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 极简 WAV 解析，用来吃下 TTS 返回的 wav。只支持 PCM(1) 和 IEEE float(3)。 */
public final class WavCodec {
    private WavCodec() {
    }

    public static class Wav {
        public final short[] mono;
        public final int sampleRate;

        Wav(short[] mono, int sampleRate) {
            this.mono = mono;
            this.sampleRate = sampleRate;
        }
    }

    public static Wav decode(byte[] data) {
        if (data == null || data.length < 44) {
            throw new IllegalArgumentException("wav 数据太短: " + (data == null ? 0 : data.length));
        }
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt() != 0x46464952) { // "RIFF"
            throw new IllegalArgumentException("不是 RIFF 文件");
        }
        bb.getInt(); // riff size
        if (bb.getInt() != 0x45564157) { // "WAVE"
            throw new IllegalArgumentException("不是 WAVE 文件");
        }

        int audioFormat = -1;
        int channels = 1;
        int sampleRate = 16000;
        int bitsPerSample = 16;

        while (bb.remaining() >= 8) {
            int id = bb.getInt();
            int size = bb.getInt();
            boolean isData = id == 0x61746164; // "data"
            // 流式合成的 wav 头写不出真实长度，data 长度常见写 0 或 0xFFFFFFFF(-1)，
            // 照着取就是 0 个采样，整句话凭空消失。这两种都按"剩下全是音频"处理
            if (size < 0 || size > bb.remaining() || (isData && size == 0)) {
                size = bb.remaining();
            }
            if (id == 0x20746d66) { // "fmt "
                int start = bb.position();
                audioFormat = bb.getShort() & 0xffff;
                channels = bb.getShort() & 0xffff;
                sampleRate = bb.getInt();
                bb.getInt();   // byte rate
                bb.getShort(); // block align
                bitsPerSample = bb.getShort() & 0xffff;
                bb.position(start + size);
            } else if (isData) {
                byte[] pcm = new byte[size];
                bb.get(pcm);
                return new Wav(toMono(pcm, audioFormat, channels, bitsPerSample), sampleRate);
            } else {
                bb.position(bb.position() + size + (size & 1));
            }
        }
        throw new IllegalArgumentException("wav 里没有 data 块");
    }

    private static short[] toMono(byte[] pcm, int audioFormat, int channels, int bits) {
        ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        int bytesPerSample = bits / 8;
        int frames = pcm.length / bytesPerSample / channels;
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += readSample(bb, audioFormat, bits);
            }
            double v = sum / channels;
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v)));
        }
        return out;
    }

    private static double readSample(ByteBuffer bb, int audioFormat, int bits) {
        if (audioFormat == 3) {
            return bits == 64 ? bb.getDouble() * Short.MAX_VALUE : bb.getFloat() * Short.MAX_VALUE;
        }
        switch (bits) {
            case 8:
                return ((bb.get() & 0xff) - 128) * 256;
            case 16:
                return bb.getShort();
            case 24: {
                int b0 = bb.get() & 0xff;
                int b1 = bb.get() & 0xff;
                int b2 = bb.get();
                return ((b2 << 16) | (b1 << 8) | b0) >> 8;
            }
            case 32:
                return bb.getInt() >> 16;
            default:
                throw new IllegalArgumentException("不支持的位深: " + bits);
        }
    }
}
