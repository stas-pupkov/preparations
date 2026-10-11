package ru.stas.pupkov.preparations.oop.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Три разных механизма, которые на собесе часто смешивают под словом «полиморфизм».
 */
public class PolymorphismKindsDemo {

    interface Painter {
        String draw();
    }

    static final class Circle implements Painter {
        @Override
        public String draw() {
            return "circle";
        }
    }

    static final class Square implements Painter {
        @Override
        public String draw() {
            return "square";
        }
    }

    /** Ad hoc: overload — разные сигнатуры, выбор на compile-time. */
    static String describe(int value) {
        return "int:" + value;
    }

    static String describe(String value) {
        return "string:" + value;
    }

    /** Parametric: один алгоритм для разных T (generics, erase на runtime). */
    static <T> void addTwice(List<? super T> target, T value) {
        target.add(value);
        target.add(value);
    }

    public static void main(String[] args) {
        System.out.println("--- subtype (runtime) ---");
        Painter shape = new Circle();
        System.out.println(shape.draw());
        shape = new Square();
        System.out.println(shape.draw());

        System.out.println("--- ad hoc / overload (compile-time) ---");
        System.out.println(describe(42));
        System.out.println(describe("x"));

        System.out.println("--- parametric / generics ---");
        List<Object> bag = new ArrayList<>();
        addTwice(bag, "item");
        System.out.println(bag);
    }
}
