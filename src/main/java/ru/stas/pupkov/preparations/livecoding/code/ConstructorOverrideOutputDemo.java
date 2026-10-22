package ru.stas.pupkov.preparations.livecoding.code;

/**
 * Вывод {@code new B()}:
 * <pre>
 * A
 * 1
 * B
 * 11
 * </pre>
 *
 * <p>Из конструктора {@code A} вызывается переопределённый {@code B.move()}, хотя тело
 * конструктора {@code B} ещё не выполнилось ({@code speed} ещё 0).
 */
public class ConstructorOverrideOutputDemo {

    public static void main(String[] args) {
        System.out.println("=== new B() ===");
        new B();
    }

    public static class A {
        protected int speed;

        public A() {
            System.out.println("A");
            move();
        }

        public void move() {
            System.out.println(speed);
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    public static class B extends A {

        public B() {
            this.speed = 10;
            System.out.println("B");
            move();
        }

        @Override
        public void move() {
            this.speed++;
            System.out.println(speed);
        }
    }
}
