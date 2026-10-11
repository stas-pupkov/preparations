package ru.stas.pupkov.preparations.java8.code;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/**
 * Захват переменных лямбдой: effectively final, мутация содержимого, {@code this}.
 */
public class LambdaCaptureDemo {

    private final String name = "LambdaCaptureDemo";

    public static void main(String[] args) {
        new LambdaCaptureDemo().run();
    }

    private void run() {
        int factor = 3;
        IntUnaryOperator mul = x -> x * factor;
        System.out.println("capture primitive: " + mul.applyAsInt(10));

        // factor = 4; // не скомпилируется: factor перестанет быть effectively final

        List<String> acc = new ArrayList<>();
        Consumer<String> append = acc::add;
        append.accept("a");
        append.accept("b");
        System.out.println("mutate content via captured ref: " + acc);

        Runnable printThis = () -> System.out.println("this in lambda: " + this.name);
        printThis.run();

        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println("this in anonymous: " + this.getClass().getName());
            }
        };
        anonymous.run();
    }
}
