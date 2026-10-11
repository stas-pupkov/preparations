package ru.stas.pupkov.preparations.java8.code;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Виды method / constructor references.
 */
public class MethodReferenceDemo {

    public static void main(String[] args) {
        Function<String, Integer> parse = Integer::parseInt;
        System.out.println("static: " + parse.apply("42"));

        List<String> sink = new ArrayList<>();
        ConsumerBound bound = sink::add;
        bound.accept("bound-instance");
        System.out.println("bound instance -> " + sink);

        Function<String, Integer> length = String::length;
        System.out.println("unbound instance: " + length.apply("abcd"));

        BiPredicate<String, String> equals = String::equals;
        System.out.println("unbound BiPredicate: " + equals.test("x", "x"));

        Supplier<List<String>> newList = ArrayList::new;
        IntFunction<String[]> newArray = String[]::new;
        System.out.println("ctor list class: " + newList.get().getClass().getSimpleName());
        System.out.println("array ctor length: " + newArray.apply(3).length);

        System.out.print("System.out::println => ");
        java.util.function.Consumer<String> printer = System.out::println;
        printer.accept("hello");
    }

    @FunctionalInterface
    private interface ConsumerBound {
        void accept(String value);
    }
}
