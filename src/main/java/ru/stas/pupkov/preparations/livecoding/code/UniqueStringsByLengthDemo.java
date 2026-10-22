package ru.stas.pupkov.preparations.livecoding.code;

import java.util.Comparator;
import java.util.List;

/**
 * Unique strings: length asc, then lexicographic.
 * Example → [fig, kiwi, apple, grape, banana].
 */
public class UniqueStringsByLengthDemo {

    public static void main(String[] args) {
        List<String> input = List.of("apple", "banana", "kiwi", "apple", "fig", "grape");
        List<String> out = uniqueSorted(input);
        System.out.println(out);
        if (!out.equals(List.of("fig", "kiwi", "apple", "grape", "banana"))) {
            throw new AssertionError("unexpected: " + out);
        }
    }

    static List<String> uniqueSorted(List<String> input) {
        return input.stream()
                .distinct()
                .sorted(Comparator
                        .comparingInt(String::length)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }
}
