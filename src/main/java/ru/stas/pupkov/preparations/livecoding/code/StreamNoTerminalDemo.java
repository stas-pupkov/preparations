package ru.stas.pupkov.preparations.livecoding.code;

import java.util.List;

/**
 * Без terminal operation pipeline не выполняется → {@code peek} молчит, печатается только список.
 */
public class StreamNoTerminalDemo {

    public static void main(String[] args) {
        List<String> animals = List.of("dog", "cat", "parrot");
        animals.stream()
                .sorted()
                .peek(item -> System.out.println(item))
                .map(String::length);
        System.out.println(animals);
    }
}
