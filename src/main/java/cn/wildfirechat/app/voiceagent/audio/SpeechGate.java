package cn.wildfirechat.app.voiceagent.audio;

/**
 * 基于能量的说话检测，只用于打断（barge-in）判定。
 *
 * 真正的断句交给 wf-voice 里的 Silero VAD，但那个要等一段话说完才出结果，
 * 对打断来说太慢。这里在通话线程上做一个极廉价的 RMS 判断：连续若干帧超过阈值
 * 就认为用户开口了，立刻掐掉正在播的 AI 语音。
 *
 * 客户端有回声消除，服务端收到的基本是纯净的用户麦克风信号，所以阈值可以给得比较低；
 * 但为了保险，要求连续多帧命中，避免一声咳嗽就把 AI 打断。
 */
public class SpeechGate {
    private final double threshold;
    private final int requiredFrames;
    private int hits;
    /** 已触发状态：一次开口只打断一次，安静一帧后重新武装，避免持续说话期间反复触发 */
    private boolean triggered;

    public SpeechGate(double threshold, int requiredFrames) {
        this.threshold = threshold;
        this.requiredFrames = requiredFrames;
    }

    /** 送入一帧，返回 true 表示确认用户正在说话。 */
    public boolean feed(short[] mono) {
        if (Pcm.rms(mono) >= threshold) {
            if (triggered) {
                return false;
            }
            hits++;
            if (hits >= requiredFrames) {
                hits = 0;
                triggered = true;
                return true;
            }
        } else {
            hits = 0;
            triggered = false;
        }
        return false;
    }

    public void reset() {
        hits = 0;
        triggered = false;
    }
}
