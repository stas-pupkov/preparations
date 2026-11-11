package ru.stas.pupkov.preparations.concurrency.code;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Демо virtual threads (Java 21+): one-task-one-thread на блокирующем I/O-подобном wait.
 * Требует {@code --enable-preview} только для preview-эпох; в Java 21+ API стабилен.
 *
 * <p>Запуск: {@code java .../VirtualThreadsDemo.java} (JDK 21+).
 */
public class VirtualThreadsDemo {

    private static final int TASKS = 10_000;

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + Runtime.version());
        System.out.println("isVirtual(main) = " + Thread.currentThread().isVirtual());

        AtomicInteger done = new AtomicInteger();

        // 1) Один virtual thread
        Thread vt = Thread.ofVirtual()
                .name("vt-demo-", 0)
                .start(() -> {
                    System.out.println("started: name=" + Thread.currentThread().getName()
                            + ", virtual=" + Thread.currentThread().isVirtual());
                    sleep(Duration.ofMillis(50));
                });
        vt.join();

        // 2) Массовый fan-out: на каждую задачу — свой VT (типичная модель для blocking I/O)
        long started = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(TASKS);
            for (int i = 0; i < TASKS; i++) {
                futures.add(executor.submit(() -> {
                    sleep(Duration.ofMillis(100)); // имитация блокирующего I/O
                    done.incrementAndGet();
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }
        long ms = (System.nanoTime() - started) / 1_000_000;
        System.out.println("completed=" + done.get() + " tasks in ~" + ms + " ms"
                + " (ожидание ~100ms wall-time при достаточных carrier threads)");

        // 3) Thread.startVirtualThread — короткий путь без имени/билдера
        Thread.startVirtualThread(() ->
                System.out.println("startVirtualThread: virtual=" + Thread.currentThread().isVirtual())
        ).join();
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
