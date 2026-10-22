package ru.stas.pupkov.preparations.livecoding.code;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Скрин 1: гонка на {@code counter++} → значения &lt; 100_000.
 * Скрин 2: {@code synchronized (latch)} → ровно 100_000 / 100_000.
 */
public class IncrementRaceVsSyncDemo {

    private static final int TASKS = 100_000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== without sync (race) ===");
        int[] raced = run(false);
        System.out.println(raced[0]);
        System.out.println(raced[1]);

        System.out.println("=== synchronized(latch) ===");
        int[] synced = run(true);
        System.out.println(synced[0]);
        System.out.println(synced[1]);
    }

    private static int[] run(boolean withSync) throws InterruptedException {
        Holder h = new Holder();
        CountDownLatch latch = new CountDownLatch(TASKS);
        ExecutorService executor = Executors.newFixedThreadPool(100);
        for (int i = 0; i < TASKS; i++) {
            executor.submit(() -> {
                if (withSync) {
                    synchronized (latch) {
                        h.counter1++;
                        h.counter2++;
                        latch.countDown();
                    }
                } else {
                    h.counter1++;
                    h.counter2++;
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        return new int[] {h.counter1, h.counter2};
    }

    private static final class Holder {
        int counter1;
        int counter2;
    }
}
