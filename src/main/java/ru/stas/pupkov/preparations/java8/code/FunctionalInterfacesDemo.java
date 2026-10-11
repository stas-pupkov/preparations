package ru.stas.pupkov.preparations.java8.code;

import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Композиция Function/Predicate и примитивные специализации.
 */
public class FunctionalInterfacesDemo {

    public static void main(String[] args) {
        Function<String, Integer> toInt = Integer::valueOf;
        Function<Integer, Integer> square = x -> x * x;
        Function<String, Integer> andThen = toInt.andThen(square);
        Function<String, Integer> compose = square.compose(toInt);
        System.out.println("andThen: " + andThen.apply("12"));
        System.out.println("compose: " + compose.apply("12"));

        Predicate<String> nonEmpty = s -> !s.isEmpty();
        Predicate<String> shortStr = s -> s.length() < 5;
        System.out.println("and: " + nonEmpty.and(shortStr).test("hi"));
        System.out.println("or: " + nonEmpty.or(shortStr).test(""));

        IntUnaryOperator twice = x -> x * 2;
        IntPredicate even = x -> (x & 1) == 0;
        System.out.println("primitive pipeline: " + even.test(twice.applyAsInt(5)));

        ToIntFunction<String> len = String::length;
        System.out.println("ToIntFunction (no Integer box in signature): " + len.applyAsInt("java8"));
    }
}
