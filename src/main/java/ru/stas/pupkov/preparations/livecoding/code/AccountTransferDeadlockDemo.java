package ru.stas.pupkov.preparations.livecoding.code;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Встречный порядок {@code synchronized(a1)/synchronized(a2)} → deadlock.
 * {@code awaitTermination(5s)} → false → println балансов не вызывается.
 */
public class AccountTransferDeadlockDemo {

    public static class Account {
        public final UUID id = UUID.randomUUID();
        public int money;
    }

    private static void move(Account from, Account to, int summa) {
        synchronized (from) {
            synchronized (to) {
                from.money -= summa;
                to.money += summa;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account a1 = new Account();
        Account a2 = new Account();

        ExecutorService e = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 1000; i++) {
            e.submit(() -> move(a1, a2, 10));
            e.submit(() -> move(a2, a1, 10));
        }

        e.shutdown();
        if (e.awaitTermination(5, TimeUnit.SECONDS)) {
            System.out.println(a1.money + " " + a2.money);
        } else {
            System.out.println("awaitTermination=false (типичный deadlock; в исходнике консоль пустая)");
        }
        System.exit(0);
    }
}
