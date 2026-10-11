package ru.stas.pupkov.preparations.concurrency.code;

/**
 * Корректный DCL требует {@code volatile} на ссылке singleton'а (Java 5+ memory model).
 * Без volatile возможен «недоинициализированный» объект у читателя.
 */
public class DoubleCheckedLockingDemo {

    static final class CorrectDcl {
        private static volatile CorrectDcl instance;
        private final long createdAt = System.nanoTime();

        private CorrectDcl() {
        }

        static CorrectDcl getInstance() {
            CorrectDcl local = instance;
            if (local == null) {
                synchronized (CorrectDcl.class) {
                    local = instance;
                    if (local == null) {
                        instance = local = new CorrectDcl();
                    }
                }
            }
            return local;
        }

        long createdAt() {
            return createdAt;
        }
    }

    /** Holder idiom: инициализация класса даёт безопасную публикацию без явного sync в get. */
    static final class HolderSingleton {
        private HolderSingleton() {
        }

        private static final class Holder {
            private static final HolderSingleton INSTANCE = new HolderSingleton();
        }

        static HolderSingleton getInstance() {
            return Holder.INSTANCE;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[8];
        CorrectDcl[] results = new CorrectDcl[threads.length];
        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> results[idx] = CorrectDcl.getInstance());
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        CorrectDcl first = results[0];
        boolean same = true;
        for (CorrectDcl r : results) {
            same &= r == first;
        }
        System.out.println("DCL same instance = " + same + ", createdAt=" + first.createdAt());
        System.out.println("Holder same refs  = "
                + (HolderSingleton.getInstance() == HolderSingleton.getInstance()));
    }
}
