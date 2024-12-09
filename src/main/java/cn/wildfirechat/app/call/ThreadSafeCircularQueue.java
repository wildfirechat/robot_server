package cn.wildfirechat.app.call;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadSafeCircularQueue<T> {
    private Object[] queue;
    private int head;
    private int tail;
    private int capacity;
    private final ReentrantLock lock = new ReentrantLock();

    public ThreadSafeCircularQueue(int capacity) {
        this.capacity = capacity+1;
        queue = new Object[this.capacity];
        head = 0;
        tail = 0;
    }

    public boolean isFull() {
        return (tail + 1) % capacity == head;
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public synchronized T enqueue(T value) {
        T owValue = null;
        if(isFull()) {
            owValue = (T) queue[head];
            head = (head + 1) % capacity;
        }
        // 覆盖最旧的数据或插入新元素
        queue[tail] = value;
        tail = (tail + 1) % capacity;
        notifyAll();
        return owValue;
    }

    public synchronized T dequeue(long waitMills) {
        if (isEmpty()) {
            // 如果队列为空，等待直到有数据可用
            try {
                if(waitMills > 0) {
                    wait(waitMills);
                } else {
                    wait();
                }
            } catch (InterruptedException e) {
                return null;
            }
            if(isEmpty()) {
                return null;
            }
        }
        // 移除并返回最旧的数据
        T value = (T) queue[head];
        head = (head + 1) % capacity;
        return value;
    }
}