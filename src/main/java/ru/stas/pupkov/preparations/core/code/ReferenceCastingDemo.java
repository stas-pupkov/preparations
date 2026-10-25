package ru.stas.pupkov.preparations.core.code;

/**
 * Widening / narrowing reference: compile-time тип ссылки vs runtime-тип объекта.
 */
public class ReferenceCastingDemo {

    static class Animal {
        String kind() {
            return "animal";
        }
    }

    static class Dog extends Animal {
        String bark() {
            return "woof";
        }

        @Override
        String kind() {
            return "dog";
        }
    }

    static class Cat extends Animal {
        String meow() {
            return "meow";
        }

        @Override
        String kind() {
            return "cat";
        }
    }

    public static void main(String[] args) {
        Animal a = new Dog(); // widening: compile-time Animal, runtime Dog
        System.out.println("kind via Animal ref: " + a.kind());
        // a.bark(); // не компилируется: у Animal нет bark()

        Dog d = (Dog) a; // narrowing: runtime — Dog → ok
        System.out.println("bark via Dog ref: " + d.bark());

        try {
            Cat c = (Cat) a; // компилируется, но runtime — Dog → ClassCastException
            System.out.println(c.meow());
        } catch (ClassCastException e) {
            System.out.println("cast to Cat failed: " + e);
        }

        if (a instanceof Dog dog) {
            System.out.println("safe pattern: " + dog.bark());
        }

        // несовместимые типы — ошибка компиляции, не ClassCastException:
        // String s = (String) a;
    }
}
