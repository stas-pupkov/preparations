package ru.stas.pupkov.preparations.jvm.code;

/**
 * Per-thread JVM stack: глубокая рекурсия исчерпывает frames → {@link StackOverflowError}.
 *
 * <p>Глубина зависит от {@code -Xss} и размера frame (locals / сложность метода).
 */
public class StackOverflowDemo {

    private static int depth;

    public static void main(String[] args) {
        try {
            recurse();
        } catch (StackOverflowError error) {
            System.out.println("Caught: " + error.getClass().getName());
            System.out.println("Approx. Java frames before SOE: " + depth);
            System.out.println("Note: native frames / JVM internals make this count approximate");
        }
    }

    private static void recurse() {
        depth++;
        recurse();
    }
}
