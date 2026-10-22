package ru.stas.pupkov.preparations.livecoding.code;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Модель агрегирующего endpoint под ограничения задачи:
 * <ul>
 *   <li>S1 (DB) — долгий;</li>
 *   <li>S2 — нельзя заливать высоким RPS (bulkhead/semaphore);</li>
 *   <li>S3 — случайные 503 → ограниченный retry;</li>
 *   <li>кэш + singleflight, чтобы UI-RPS не равнялся RPS в origins.</li>
 * </ul>
 *
 * <p>Тайминги сжаты (мс вместо секунд), чтобы демо заканчивалось быстро.
 */
public class AggregateEndpointResilienceDemo {

    /** Сжатые «секунды» задачи: 15с / 5с / 10с → 15 / 5 / 10 мс. */
    private static final long DB_MS = 15;
    private static final long S2_MS = 5;
    private static final long S3_MS = 10;

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        OriginClient origins = new OriginClient();
        Aggregator api = new Aggregator(origins, pool, /* max in-flight to S2 */ 2);

        System.out.println("=== 1) cache miss: parallel fan-out ===");
        long t0 = System.nanoTime();
        Aggregate first = api.get("dashboard-1").get(2, TimeUnit.SECONDS);
        long missMs = elapsedMs(t0);
        System.out.println(first + " latency≈" + missMs + "ms (ожидаем ~max(15,5,10)=15, не сумму)");

        System.out.println("=== 2) cache hit: UI RPS не бьёт origins ===");
        int before = origins.totalCalls();
        t0 = System.nanoTime();
        Aggregate hit = api.get("dashboard-1").get(2, TimeUnit.SECONDS);
        System.out.println(hit + " latency≈" + elapsedMs(t0) + "ms, originCallsDelta="
                + (origins.totalCalls() - before));

        System.out.println("=== 3) singleflight: 20 параллельных miss → один fan-out ===");
        api.evict("dashboard-2");
        before = origins.totalCalls();
        List<CompletableFuture<Aggregate>> storm = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            storm.add(api.get("dashboard-2"));
        }
        CompletableFuture.allOf(storm.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
        int delta = origins.totalCalls() - before;
        System.out.println("originCalls for 20 requests=" + delta + " (ожидаем ~3, по одному на источник)");

        System.out.println("=== 4) S2 bulkhead: лишние refresh не пробивают лимит ===");
        int rejected = 0;
        List<CompletableFuture<String>> s2Calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            s2Calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return origins.fetchS2Protected(api.s2Bulkhead());
                } catch (Exception e) {
                    return "REJECTED";
                }
            }, pool));
        }
        for (CompletableFuture<String> f : s2Calls) {
            if ("REJECTED".equals(f.get(2, TimeUnit.SECONDS))) {
                rejected++;
            }
        }
        System.out.println("S2 rejected by bulkhead=" + rejected + " (при in-flight limit=2 часть должна отсечься)");

        System.out.println("=== 5) S3: 503 затем успех на retry ===");
        origins.forceNextS3Failures(1);
        String s3 = origins.fetchS3WithRetry(2);
        System.out.println("S3 after retry=" + s3 + ", s3Attempts=" + origins.s3Attempts());

        pool.shutdownNow();
        System.out.println("AggregateEndpointResilienceDemo: OK");
    }

    private static long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    record Aggregate(String db, String s2, String s3) {
    }

    static final class Aggregator {
        private final OriginClient origins;
        private final ExecutorService pool;
        private final Semaphore s2Bulkhead;
        private final Map<String, Aggregate> cache = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<Aggregate>> inFlight = new ConcurrentHashMap<>();

        Aggregator(OriginClient origins, ExecutorService pool, int s2MaxInFlight) {
            this.origins = origins;
            this.pool = pool;
            this.s2Bulkhead = new Semaphore(s2MaxInFlight);
        }

        Semaphore s2Bulkhead() {
            return s2Bulkhead;
        }

        void evict(String key) {
            cache.remove(key);
        }

        /**
         * UI-запрос: hit из кэша или один shared Future (singleflight).
         *
         * @param key ключ агрегата
         * @return future результата
         */
        CompletableFuture<Aggregate> get(String key) {
            Aggregate cached = cache.get(key);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            return inFlight.computeIfAbsent(key, this::loadAsync);
        }

        private CompletableFuture<Aggregate> loadAsync(String key) {
            CompletableFuture<String> db = CompletableFuture.supplyAsync(origins::fetchDb, pool);
            CompletableFuture<String> s2 = CompletableFuture.supplyAsync(
                    () -> origins.fetchS2Protected(s2Bulkhead), pool);
            CompletableFuture<String> s3 = CompletableFuture.supplyAsync(
                    () -> origins.fetchS3WithRetry(2), pool);

            return CompletableFuture.allOf(db, s2, s3)
                    .thenApply(v -> new Aggregate(db.join(), s2.join(), s3.join()))
                    .whenComplete((agg, err) -> {
                        if (agg != null) {
                            cache.put(key, agg);
                        }
                        inFlight.remove(key);
                    });
        }
    }

    static final class OriginClient {
        private final AtomicInteger dbCalls = new AtomicInteger();
        private final AtomicInteger s2Calls = new AtomicInteger();
        private final AtomicInteger s3Calls = new AtomicInteger();
        private final AtomicInteger s3Attempts = new AtomicInteger();
        private final AtomicInteger forcedS3Failures = new AtomicInteger();

        int totalCalls() {
            return dbCalls.get() + s2Calls.get() + s3Calls.get();
        }

        int s3Attempts() {
            return s3Attempts.get();
        }

        void forceNextS3Failures(int n) {
            forcedS3Failures.set(n);
        }

        String fetchDb() {
            dbCalls.incrementAndGet();
            sleep(DB_MS);
            return "db-ok";
        }

        /**
         * S2 под bulkhead: без разрешения — сразу reject (лучше stale, чем ждать 2 мин).
         *
         * @param bulkhead семафор исходящих вызовов
         * @return payload
         */
        String fetchS2Protected(Semaphore bulkhead) {
            if (!bulkhead.tryAcquire()) {
                throw new IllegalStateException("S2 bulkhead full");
            }
            try {
                s2Calls.incrementAndGet();
                sleep(S2_MS);
                return "s2-ok";
            } finally {
                bulkhead.release();
            }
        }

        /**
         * S3 с ограниченным retry на «503».
         *
         * @param maxAttempts максимум попыток
         * @return payload
         */
        String fetchS3WithRetry(int maxAttempts) {
            RuntimeException last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                s3Attempts.incrementAndGet();
                try {
                    return fetchS3Once();
                } catch (ServiceUnavailableException ex) {
                    last = ex;
                    sleep(1L * attempt);
                }
            }
            throw last != null ? last : new IllegalStateException("S3 failed");
        }

        private String fetchS3Once() {
            s3Calls.incrementAndGet();
            sleep(S3_MS);
            if (forcedS3Failures.getAndDecrement() > 0) {
                throw new ServiceUnavailableException("503 from S3");
            }
            return "s3-ok";
        }

        private static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    static final class ServiceUnavailableException extends RuntimeException {
        ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
