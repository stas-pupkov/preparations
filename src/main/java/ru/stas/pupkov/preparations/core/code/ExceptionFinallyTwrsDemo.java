package ru.stas.pupkov.preparations.core.code;

import java.io.IOException;

/**
 * {@code finally} и return; suppressed exceptions в try-with-resources; порядок catch.
 */
public class ExceptionFinallyTwrsDemo {

    static final class TraceResource implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        TraceResource(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("open " + name);
        }

        void work(boolean fail) throws IOException {
            System.out.println("work " + name);
            if (fail) {
                throw new IOException("work failed in " + name);
            }
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            if (failOnClose) {
                throw new IOException("close failed in " + name);
            }
        }
    }

    static int finallyOverridesReturn() {
        try {
            return 1;
        } finally {
            System.out.println("finally runs before method really returns");
            // return в finally затирает return из try — антипаттерн
            return 2;
        }
    }

    static void throwIo(boolean fileNotFound) throws IOException {
        if (fileNotFound) {
            throw new java.io.FileNotFoundException("missing");
        }
        throw new IOException("generic io");
    }

    static void catchOrder(boolean fileNotFound) {
        try {
            throwIo(fileNotFound);
        } catch (java.io.FileNotFoundException ex) {
            System.out.println("caught FileNotFoundException (more specific first)");
        } catch (IOException ex) {
            System.out.println("caught IOException");
        }
    }

    public static void main(String[] args) {
        System.out.println("--- finally return wins ---");
        System.out.println("result: " + finallyOverridesReturn());

        System.out.println("--- catch order: subclass before superclass ---");
        catchOrder(true);
        catchOrder(false);

        System.out.println("--- try-with-resources: primary + suppressed ---");
        try (TraceResource outer = new TraceResource("outer", true);
             TraceResource inner = new TraceResource("inner", true)) {
            outer.work(true);
        } catch (IOException ex) {
            System.out.println("primary: " + ex);
            Throwable[] suppressed = ex.getSuppressed();
            System.out.println("suppressed count: " + suppressed.length);
            for (Throwable s : suppressed) {
                System.out.println("  suppressed: " + s);
            }
        }

        System.out.println("--- close order is reverse of open ---");
        try (TraceResource a = new TraceResource("A", false);
             TraceResource b = new TraceResource("B", false)) {
            a.work(false);
            b.work(false);
        } catch (IOException ex) {
            System.out.println("unexpected: " + ex);
        }
    }
}
