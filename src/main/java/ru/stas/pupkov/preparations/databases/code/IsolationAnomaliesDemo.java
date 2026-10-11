package ru.stas.pupkov.preparations.databases.code;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Иллюстрация аномалий параллельного доступа на in-memory «таблице».
 */
public class IsolationAnomaliesDemo {

    public static void main(String[] args) {
        dirtyRead();
        nonRepeatableRead();
        phantomRead();
        lostUpdate();
    }

    private static void dirtyRead() {
        mutableInt balance = new mutableInt(100);
        Thread writer = new Thread(() -> {
            balance.value = 1_000_000; // uncommitted change
            sleep(80);
            balance.value = 100; // rollback
        });
        writer.start();
        sleep(20);
        int dirty = balance.value;
        join(writer);
        System.out.println("dirty read saw=" + dirty + ", final=" + balance.value
                + " (anomaly if dirty != final and dirty was uncommitted)");
    }

    private static void nonRepeatableRead() {
        mutableInt row = new mutableInt(10);
        int first = row.value;
        row.value = 20; // concurrent committed update
        int second = row.value;
        System.out.println("non-repeatable read: first=" + first + " second=" + second);
    }

    private static void phantomRead() {
        List<Integer> table = new CopyOnWriteArrayList<>(List.of(10, 20));
        int sum1 = table.stream().mapToInt(Integer::intValue).sum();
        table.add(30); // concurrent insert matching predicate "all rows"
        int sum2 = table.stream().mapToInt(Integer::intValue).sum();
        System.out.println("phantom read: sum1=" + sum1 + " sum2=" + sum2 + " rows=" + table);
    }

    private static void lostUpdate() {
        mutableInt counter = new mutableInt(0);
        Runnable increment = () -> {
            int local = counter.value; // read
            sleep(30);
            counter.value = local + 1; // write without re-read
        };
        Thread t1 = new Thread(increment);
        Thread t2 = new Thread(increment);
        t1.start();
        t2.start();
        join(t1);
        join(t2);
        System.out.println("lost update: expected 2, actual=" + counter.value);
    }

    private static final class mutableInt {
        volatile int value;

        mutableInt(int value) {
            this.value = value;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
