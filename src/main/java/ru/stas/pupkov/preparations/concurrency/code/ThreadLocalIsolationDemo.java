package ru.stas.pupkov.preparations.concurrency.code;

/**
 * У каждого потока своё значение {@link ThreadLocal}.
 * Важно: в пулах потоков значение нужно чистить ({@code remove()}), иначе возможен leak / «грязь».
 */
public class ThreadLocalIsolationDemo {

    private static final ThreadLocal<String> CTX = ThreadLocal.withInitial(() -> "unset");

    public static void main(String[] args) throws InterruptedException {
        CTX.set("main");

        Thread worker = new Thread(() -> {
            System.out.println("worker initial = " + CTX.get());
            CTX.set("worker");
            System.out.println("worker after set = " + CTX.get());
            CTX.remove();
            System.out.println("worker after remove = " + CTX.get());
        }, "worker");

        worker.start();
        worker.join();
        System.out.println("main still = " + CTX.get());
        CTX.remove();
    }
}
