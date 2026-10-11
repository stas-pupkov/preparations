package ru.stas.pupkov.preparations.concurrency.code;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Когда core/max заняты и очередь полна — срабатывает {@link java.util.concurrent.RejectedExecutionHandler}.
 * По умолчанию AbortPolicy → {@link RejectedExecutionException}.
 */
public class ThreadPoolRejectionDemo {

    public static void main(String[] args) {
        AtomicInteger started = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                r -> {
                    Thread t = new Thread(r, "worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            // 1 — в работу, 1 — в очередь, 3-я — reject
            for (int i = 1; i <= 3; i++) {
                final int taskId = i;
                try {
                    pool.execute(() -> {
                        started.incrementAndGet();
                        sleep(300);
                        System.out.println("finished task " + taskId);
                    });
                    System.out.println("accepted task " + taskId);
                } catch (RejectedExecutionException ex) {
                    System.out.println("rejected task " + taskId + ": " + ex.getClass().getSimpleName());
                }
            }
            sleep(500);
            System.out.println("started count=" + started.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
