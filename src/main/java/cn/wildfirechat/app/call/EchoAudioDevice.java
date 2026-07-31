package cn.wildfirechat.app.call;

import cn.wildfirechat.AudioDevice;
import cn.wildfirechat.CallSession;
import cn.wildfirechat.pojos.Conversation;

import java.util.concurrent.LinkedBlockingQueue;

public class EchoAudioDevice implements AudioDevice {
    // Bounded queue (10s of 10ms packets): oldest packets are dropped when full,
    // an unbounded queue would grow forever if playout outpaces recording
    private static final int MAX_QUEUE_SIZE = 1000;
    private final LinkedBlockingQueue<byte[]> cacheQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final Conversation conversation;

    public EchoAudioDevice(Conversation conversation) {
        this.conversation = conversation;
    }

    @Override
    public int initPlayout(CallSession callSession, String userId) {
        return 0;
    }

    @Override
    public int stopPlayout(CallSession callSession, String userId) {
        return 0;
    }

    @Override
    public int initRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int startRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public int stopRecording(CallSession callSession) {
        return 0;
    }

    @Override
    public void fetchRecordData(CallSession callSession, byte[] sampleData, int nSamples, int nSampleBytes, int nChannels, int nSampleRate, int nBuffSize) {
        if(cacheQueue.size() > 300) {
            byte[] data = cacheQueue.poll();
            if(data != null && data.length == nBuffSize) {
                System.arraycopy(data, 0, sampleData, 0, nBuffSize);
            } else {
                System.out.println("data size error");
            }
        } else {
            for (int i = 0; i < nBuffSize; i++) {
                sampleData[i] = 0;
            }
        }
    }

    @Override
    public void playoutData(CallSession callSession, String userId, byte[] sampleData, int nBuffSize) {
        byte[] data = new byte[nBuffSize];
        System.arraycopy(sampleData, 0, data, 0, nBuffSize);
        if (!cacheQueue.offer(data)) {
            // Queue full: drop the oldest packet to make room
            cacheQueue.poll();
            cacheQueue.offer(data);
        }
    }
}
