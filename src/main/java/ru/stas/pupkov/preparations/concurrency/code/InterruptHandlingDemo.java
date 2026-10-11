package ru.stas.pupkov.preparations.concurrency.code;

/**
 * {@code Thread.interrupted()} сбрасывает флаг; {@code isInterrupted()} — нет.
 * При {@code InterruptedException} флаг обычно уже очищен — его нужно восстанавливать.
 */
public class InterruptHandlingDemo {

    public static void main(String[] args) throws InterruptedException {
        demoInterruptedClearsFlag();
        demoIsInterruptedKeepsFlag();
        demoRestoreAfterInterruptedException();
    }

    private static void demoInterruptedClearsFlag() {
        Thread.currentThread().interrupt();
        System.out.println("interrupted() first  = " + Thread.interrupted());
        System.out.println("interrupted() second = " + Thread.interrupted());
    }

    private static void demoIsInterruptedKeepsFlag() {
        Thread.currentThread().interrupt();
        System.out.println("isInterrupted() first  = " + Thread.currentThread().isInterrupted());
        System.out.println("isInterrupted() second = " + Thread.currentThread().isInterrupted());
        Thread.interrupted(); // очистить перед следующим сценарием
    }

    private static void demoRestoreAfterInterruptedException() throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                // Флаг уже false. Политика «пробросить намерение»:
                Thread.currentThread().interrupt();
                System.out.println("worker restored interrupt flag="
                        + Thread.currentThread().isInterrupted());
            }
        }, "sleeper");
        worker.start();
        Thread.sleep(50);
        worker.interrupt();
        worker.join();
    }
}
