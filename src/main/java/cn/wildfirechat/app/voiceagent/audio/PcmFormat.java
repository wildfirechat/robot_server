package cn.wildfirechat.app.voiceagent.audio;

/**
 * 通话音频的 PCM 格式。
 *
 * AudioDevice.playoutData 只给字节数组，不给采样率和声道数，只有 fetchRecordData
 * 才带这两个参数。EchoAudioDevice 把收到的字节原样塞回 fetchRecordData 并断言长度
 * 相等，说明收发是同一种格式，所以这里从 fetchRecordData 学到之后两个方向共用。
 */
public class PcmFormat {
    public final int sampleRate;
    public final int channels;
    public final int bytesPerSample;

    public PcmFormat(int sampleRate, int channels, int bytesPerSample) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bytesPerSample = bytesPerSample;
    }

    public boolean sameAs(PcmFormat o) {
        return o != null && o.sampleRate == sampleRate && o.channels == channels
                && o.bytesPerSample == bytesPerSample;
    }

    @Override
    public String toString() {
        return sampleRate + "Hz/" + channels + "ch/" + (bytesPerSample * 8) + "bit";
    }
}
