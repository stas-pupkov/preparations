package ru.stas.pupkov.preparations.collections.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentHashMap: параллельные put, гонка containsKey+put vs putIfAbsent, запрет null.
 */
public class ConcurrentHashMapDemo {

    public static void main(String[] args) throws InterruptedException {
        demonstrateParallelPuts();
        demonstrateContainsKeyPutRace();
        demonstrateNullForbidden();
    }

    private static void demonstrateParallelPuts() throws InterruptedException {
        System.out.println("--- parallel put в разные/одни ключи ---");
        Map<String, Integer> map = new ConcurrentHashMap<>();
        int threads = 8;
        int putsPerThread = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < putsPerThread; i++) {
                        map.put("k-" + threadId + "-" + i, i);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();
        System.out.println("expected size=" + (threads * putsPerThread) + ", actual=" + map.size());
    }

    private static void demonstrateContainsKeyPutRace() throws InterruptedException {
        System.out.println("--- гонка containsKey + put vs putIfAbsent ---");
        Map<String, String> naive = new ConcurrentHashMap<>();
        Map<String, String> safe = new ConcurrentHashMap<>();
        AtomicInteger naiveInits = new AtomicInteger();
        AtomicInteger safeInits = new AtomicInteger();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    // НЕ атомарно даже на ConcurrentHashMap (пауза расширяет окно гонки для демо)
                    if (!naive.containsKey("x")) {
                        Thread.sleep(5);
                        naiveInits.incrementAndGet();
                        naive.put("x", "created");
                    }
                    // атомарно для одной записи: mapping вызывается только при отсутствии ключа
                    safe.computeIfAbsent("x", key -> {
                        safeInits.incrementAndGet();
                        return "created";
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        System.out.println("naive containsKey+put: init attempts=" + naiveInits.get()
                + " (часто > 1 при гонке)");
        System.out.println("computeIfAbsent: init attempts=" + safeInits.get() + " (ожидаем 1)");
    }

    private static void demonstrateNullForbidden() {
        System.out.println("--- null запрещён ---");
        Map<String, String> map = new ConcurrentHashMap<>();
        try {
            map.put(null, "v");
            System.out.println("ERROR: expected NPE for null key");
        } catch (NullPointerException expected) {
            System.out.println("null key → " + expected.getClass().getSimpleName());
        }
        try {
            map.put("k", null);
            System.out.println("ERROR: expected NPE for null value");
        } catch (NullPointerException expected) {
            System.out.println("null value → " + expected.getClass().getSimpleName());
        }
    }
}
