package ru.stas.pupkov.preparations.jvm.code;

/**
 * Preparation (default values) vs initialization ({@code <clinit>}).
 *
 * <p>{@code Class.forName(name, false, loader)} не запускает static-инициализаторы.
 * Первое active use — запускает.
 */
public class ClassInitializationDemo {

    static class Holder {
        static final String MARKER;

        static int counter;

        static {
            System.out.println("Holder.<clinit> running");
            MARKER = "initialized";
            counter = 42;
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ClassInitializationDemo.class.getClassLoader();
        String name = Holder.class.getName();

        System.out.println("--- forName(..., initialize=false) ---");
        Class<?> loaded = Class.forName(name, false, loader);
        System.out.println("Class loaded: " + loaded.getName());
        System.out.println("<clinit> has NOT run yet (no print above from Holder)");

        System.out.println("--- first active use: read static field ---");
        System.out.println("Holder.counter = " + Holder.counter);
        System.out.println("Holder.MARKER = " + Holder.MARKER);

        System.out.println("--- forName(..., initialize=true) on already initialized class ---");
        Class.forName(name, true, loader);
        System.out.println("<clinit> runs at most once per Class");
    }
}
