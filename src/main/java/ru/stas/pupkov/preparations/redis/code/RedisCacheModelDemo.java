package ru.stas.pupkov.preparations.redis.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Упрощённая модель Redis: TTL-кэш и атомарный INCR vs гонка GET+SET.
 */
public class RedisCacheModelDemo {

    static final class TinyRedis {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expireAt = new ConcurrentHashMap<>();

        void set(String key, String value, long ttlMs) {
            values.put(key, value);
            expireAt.put(key, System.currentTimeMillis() + ttlMs);
        }

        String get(String key) {
            Long exp = expireAt.get(key);
            if (exp != null && System.currentTimeMillis() > exp) {
                values.remove(key);
                expireAt.remove(key);
                return null;
            }
            return values.get(key);
        }

        int incr(String key) {
            return Integer.parseInt(values.compute(key, (k, v) -> {
                int next = (v == null ? 0 : Integer.parseInt(v)) + 1;
                return Integer.toString(next);
            }));
        }
    }

    public static void main(String[] args) throws Exception {
        TinyRedis redis = new TinyRedis();
        redis.set("user:1", "Ann", 80);
        System.out.println("hit=" + redis.get("user:1"));
        Thread.sleep(100);
        System.out.println("after TTL miss=" + redis.get("user:1"));

        System.out.println("=== INCR atomic ===");
        AtomicInteger lost = new AtomicInteger();
        redis.set("counter", "0", 60_000);
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                redis.incr("counter");
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                redis.incr("counter");
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("incr result=" + redis.get("counter") + " (expect 2000)");

        System.out.println("=== naive GET+SET race (application-side) ===");
        redis.set("naive", "0", 60_000);
        Thread n1 = new Thread(() -> bumpNaive(redis, lost));
        Thread n2 = new Thread(() -> bumpNaive(redis, lost));
        n1.start();
        n2.start();
        n1.join();
        n2.join();
        System.out.println("naive result=" + redis.get("naive")
                + " (часто < 2000 из-за гонки get/set)");
    }

    private static void bumpNaive(TinyRedis redis, AtomicInteger ignored) {
        for (int i = 0; i < 1000; i++) {
            String raw = redis.get("naive");
            int v = raw == null ? 0 : Integer.parseInt(raw);
            redis.set("naive", Integer.toString(v + 1), 60_000);
        }
    }
}
