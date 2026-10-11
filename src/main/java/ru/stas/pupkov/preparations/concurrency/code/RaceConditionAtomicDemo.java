package ru.stas.pupkov.preparations.concurrency.code;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code volatile} даёт visibility, но не атомарность RMW вроде {@code count++}.
 * {@link AtomicInteger} закрывает read-modify-write через CAS.
 */
public class RaceConditionAtomicDemo {

    private static volatile int volatileCount;
    private static final AtomicInteger atomicCount = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        final int threads = 8;
        final int incrementsPerThread = 100_000;

        volatileCount = 0;
        atomicCount.set(0);

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    volatileCount++; // не атомарно
                    atomicCount.incrementAndGet();
                }
            });
        }
        for (Thread t : workers) {
            t.start();
        }
        for (Thread t : workers) {
            t.join();
        }

        int expected = threads * incrementsPerThread;
        System.out.println("expected          = " + expected);
        System.out.println("volatileCount     = " + volatileCount + " (lost updates expected)");
        System.out.println("atomicCount       = " + atomicCount.get());
    }
}
