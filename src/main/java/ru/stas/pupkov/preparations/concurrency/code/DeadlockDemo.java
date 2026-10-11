package ru.stas.pupkov.preparations.concurrency.code;

/**
 * Классический deadlock: два потока захватывают два монитора в разном порядке.
 * Демо намеренно завершает JVM через timeout, если потоки зависли.
 */
public class DeadlockDemo {

    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                sleep(50);
                synchronized (LOCK_B) {
                    System.out.println("t1 acquired A then B");
                }
            }
        }, "t1");
        t1.setDaemon(true);

        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                sleep(50);
                synchronized (LOCK_A) {
                    System.out.println("t2 acquired B then A");
                }
            }
        }, "t2");
        t2.setDaemon(true);

        t1.start();
        t2.start();

        t1.join(1_500);
        t2.join(1_500);

        if (t1.isAlive() || t2.isAlive()) {
            System.out.println("deadlock likely: threads still alive");
            System.out.println("t1 state=" + t1.getState() + ", t2 state=" + t2.getState());
            System.out.println("fix: capture locks in a global order (A then B for both)");
        } else {
            System.out.println("no deadlock this run (scheduling race)");
        }
        // Daemon-потоки: JVM сможет завершиться после main, несмотря на deadlock.
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
