package ru.stas.pupkov.preparations.concurrency.code;

/**
 * Visibility без happens-before: reader может «навсегда» не увидеть запись writer'а.
 * С {@code volatile} запись/чтение флажка дают visibility (и упорядочивание вокруг него).
 */
public class VolatileVisibilityDemo {

    private static boolean readyPlain;
    private static volatile boolean readyVolatile;
    /** Только чтобы гарантированно завершить демо, если plain-reader застрял. */
    private static volatile boolean stopPlainReader;

    public static void main(String[] args) throws InterruptedException {
        demoPlainMayHang();
        demoVolatileWorks();
    }

    private static void demoPlainMayHang() throws InterruptedException {
        readyPlain = false;
        stopPlainReader = false;
        Thread reader = new Thread(() -> {
            // Busy-wait без sync: JIT может закэшировать readyPlain в регистре.
            while (!readyPlain && !stopPlainReader) {
                // empty
            }
            if (readyPlain) {
                System.out.println("plain: reader saw ready=true");
            } else {
                System.out.println("plain: reader stopped by demo watchdog (likely never saw write)");
            }
        }, "plain-reader");
        reader.start();

        Thread.sleep(200);
        readyPlain = true;
        System.out.println("plain: writer set ready=true");

        reader.join(2_000);
        if (reader.isAlive()) {
            System.out.println("plain: reader still stuck after 2s (visibility failure likely)");
            stopPlainReader = true;
            reader.join();
        }
    }

    private static void demoVolatileWorks() throws InterruptedException {
        readyVolatile = false;
        Thread reader = new Thread(() -> {
            while (!readyVolatile) {
                // empty
            }
            System.out.println("volatile: reader saw ready=true");
        }, "volatile-reader");
        reader.start();

        Thread.sleep(50);
        readyVolatile = true;
        reader.join();
        System.out.println("volatile: done");
    }
}
