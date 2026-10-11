package ru.stas.pupkov.preparations.concurrency.code;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Ограниченный буфер на мониторе: wait в цикле (spurious wakeup / condition recheck),
 * notifyAll при смене условия для producer/consumer.
 */
public class WaitNotifyBoundedBufferDemo {

    static final class BoundedBuffer<T> {
        private final Queue<T> queue = new ArrayDeque<>();
        private final int capacity;

        BoundedBuffer(int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("capacity");
            }
            this.capacity = capacity;
        }

        synchronized void put(T value) throws InterruptedException {
            while (queue.size() == capacity) {
                wait();
            }
            queue.add(value);
            notifyAll();
        }

        synchronized T take() throws InterruptedException {
            while (queue.isEmpty()) {
                wait();
            }
            T value = queue.remove();
            notifyAll();
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.put(i);
                    System.out.println("put " + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    int value = buffer.take();
                    System.out.println("take " + value);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("buffer demo done");
    }
}
