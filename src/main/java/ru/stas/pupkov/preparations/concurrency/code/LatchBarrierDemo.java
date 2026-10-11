package ru.stas.pupkov.preparations.concurrency.code;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@link CountDownLatch} — одноразовый «старт по событию».
 * {@link CyclicBarrier} — встреча N участников, можно использовать повторно.
 */
public class LatchBarrierDemo {

    public static void main(String[] args) throws Exception {
        demoLatch();
        demoBarrier();
    }

    private static void demoLatch() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            for (int i = 1; i <= 3; i++) {
                final int id = i;
                pool.execute(() -> {
                    System.out.println("latch worker-" + id + " ready");
                    ready.countDown();
                    try {
                        start.await();
                        System.out.println("latch worker-" + id + " started");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            ready.await();
            System.out.println("latch: all ready, open gate");
            start.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static void demoBarrier() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3, () -> System.out.println("barrier: all met"));
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            for (int round = 1; round <= 2; round++) {
                final int r = round;
                for (int i = 1; i <= 3; i++) {
                    final int id = i;
                    pool.execute(() -> {
                        try {
                            System.out.println("barrier r" + r + " worker-" + id + " arrives");
                            barrier.await();
                            System.out.println("barrier r" + r + " worker-" + id + " continues");
                        } catch (Exception e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
                Thread.sleep(200);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
