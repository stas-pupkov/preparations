package ru.stas.pupkov.preparations.livecoding.code;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Check-then-act без unique → два CREATED на один key.
 * С unique-семантикой (putIfAbsent) → один CREATED, второй DUPLICATE.
 */
public class IdempotentCreateOrderRaceDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== naive find+insert (race) ===");
        runRace(new NaiveStore());

        System.out.println("=== putIfAbsent as unique (safe) ===");
        runRace(new UniqueStore());
    }

    private static void runRace(IdempotencyStore store) throws InterruptedException {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(20);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        String key = "pay-42";

        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    String status = store.create(key);
                    if ("CREATED".equals(status)) {
                        created.incrementAndGet();
                    } else {
                        duplicate.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();
        System.out.println("CREATED=" + created.get() + " DUPLICATE=" + duplicate.get()
                + " orders=" + store.orderCount());
    }

    interface IdempotencyStore {
        String create(String key);

        int orderCount();
    }

    /** Как в коде: find, if empty → create. Без уникальности. */
    static final class NaiveStore implements IdempotencyStore {
        private final Map<String, UUID> keys = new ConcurrentHashMap<>();
        private final AtomicInteger orders = new AtomicInteger();

        @Override
        public String create(String key) {
            if (keys.containsKey(key)) {
                return "DUPLICATE";
            }
            // окно гонки
            UUID orderId = UUID.randomUUID();
            orders.incrementAndGet();
            keys.put(key, orderId);
            return "CREATED";
        }

        @Override
        public int orderCount() {
            return orders.get();
        }
    }

    /** Аналог UNIQUE + insert first. */
    static final class UniqueStore implements IdempotencyStore {
        private final Map<String, UUID> keys = new ConcurrentHashMap<>();

        @Override
        public String create(String key) {
            UUID orderId = UUID.randomUUID();
            UUID prev = keys.putIfAbsent(key, orderId);
            if (prev != null) {
                return "DUPLICATE";
            }
            return "CREATED";
        }

        @Override
        public int orderCount() {
            return keys.size();
        }
    }
}
