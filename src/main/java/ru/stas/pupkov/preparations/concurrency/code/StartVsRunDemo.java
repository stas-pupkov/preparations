package ru.stas.pupkov.preparations.concurrency.code;

/**
 * {@code start()} запускает новый поток; прямой {@code run()} выполняется в вызывающем.
 */
public class StartVsRunDemo {

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> System.out.println("task in " + Thread.currentThread().getName());

        System.out.println("caller = " + Thread.currentThread().getName());

        Thread viaStart = new Thread(task, "worker");
        viaStart.start();
        viaStart.join();

        Thread viaRun = new Thread(task, "never-started");
        viaRun.run(); // тот же поток, что и main
    }
}
