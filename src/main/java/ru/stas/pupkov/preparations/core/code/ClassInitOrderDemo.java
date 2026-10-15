package ru.stas.pupkov.preparations.core.code;

/**
 * Порядок инициализации: superclass {@code <clinit>} → subclass {@code <clinit>} →
 * superclass instance fields/blocks → superclass конструктор → subclass instance → subclass конструктор.
 *
 * <p>Ошибка в static-инициализаторе → {@link ExceptionInInitializerError}.
 */
public class ClassInitOrderDemo {

    static class Parent {
        static {
            System.out.println("1 Parent.<clinit>");
        }

        {
            System.out.println("3 Parent instance block");
        }

        Parent() {
            System.out.println("4 Parent конструктор");
        }
    }

    static class Child extends Parent {
        static int childStatic = printStatic("2 Child static field");

        static {
            System.out.println("2b Child.<clinit> block");
        }

        int childInstance = printInstance("5 Child instance field");

        {
            System.out.println("5b Child instance block");
        }

        Child() {
            System.out.println("6 Child конструктор");
        }

        private static int printStatic(String msg) {
            System.out.println(msg);
            return 1;
        }

        private static int printInstance(String msg) {
            System.out.println(msg);
            return 1;
        }
    }

    static class Boom {
        static {
            if (true) {
                throw new IllegalStateException("static init failed");
            }
        }

        static void touch() {
            System.out.println("never reached");
        }
    }

    public static void main(String[] args) {
        System.out.println("--- hierarchy init order ---");
        new Child();

        System.out.println("--- ExceptionInInitializerError wraps cause ---");
        try {
            Boom.touch();
        } catch (ExceptionInInitializerError error) {
            System.out.println("caught: " + error.getClass().getSimpleName());
            System.out.println("cause: " + error.getCause());
        }

        System.out.println("--- second access after failed <clinit> ---");
        try {
            Boom.touch();
        } catch (NoClassDefFoundError error) {
            System.out.println("caught: " + error.getClass().getSimpleName()
                    + " (class marked erroneous)");
        }
    }
}
