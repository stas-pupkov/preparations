package ru.stas.pupkov.preparations.oop.code;

/**
 * Early vs late binding в Java.
 *
 * <p>Запуск: {@code ./gradlew -q run -PmainClass=ru.stas.pupkov.preparations.oop.code.BindingDemo}
 * или Run в IDE на {@code main}.
 */
public class BindingDemo {

    static class Animal {
        String kind = "Animal.field";

        static void staticMethod() {
            System.out.println("Animal.staticMethod");
        }

        private void privateMethod() {
            System.out.println("Animal.privateMethod");
        }

        final void finalMethod() {
            System.out.println("Animal.finalMethod");
        }

        void sound() {
            System.out.println("Animal.sound");
        }

        void say(int value) {
            System.out.println("Animal.say(int): " + value);
        }

        void say(String value) {
            System.out.println("Animal.say(String): " + value);
        }

        void callPrivate() {
            privateMethod();
        }
    }

    static class Dog extends Animal {
        String kind = "Dog.field";

        static void staticMethod() {
            System.out.println("Dog.staticMethod");
        }

        @Override
        void sound() {
            System.out.println("Dog.sound");
        }
    }

    public static void main(String[] args) {
        Animal ref = new Dog();

        System.out.println("--- early binding (тип ссылки) ---");
        ref.staticMethod();   // Animal.staticMethod — static не полиморфен
        ref.finalMethod();    // final — не переопределяется
        ref.say("hello");     // overload выбирается на compile-time
        System.out.println(ref.kind); // поля тоже по типу ссылки → Animal.field

        System.out.println("--- late binding (тип объекта) ---");
        ref.sound();          // Dog.sound — override через dynamic dispatch

        System.out.println("--- private всегда свой у класса ---");
        ref.callPrivate();    // вызывает Animal.privateMethod
    }
}
