package ru.stas.pupkov.preparations.java8.code;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ленивый pipeline, flatMap, collect/groupingBy, одноразовость Stream.
 */
public class StreamPipelineDemo {

    public static void main(String[] args) {
        System.out.println("--- laziness: peek before terminal does nothing until count ---");
        Stream<String> lazy = Stream.of("a", "bb", "ccc")
                .filter(s -> s.length() > 1)
                .peek(s -> System.out.println("  peek: " + s));
        System.out.println("pipeline built, no peek yet");
        System.out.println("count=" + lazy.count());

        List<String> flat = Stream.of("H e l l o", "w o r l d")
                .flatMap(line -> Arrays.stream(line.split(" ")))
                .toList();
        System.out.println("flatMap: " + flat);

        Map<Integer, List<String>> byLen = Stream.of("a", "bb", "c", "dd", "eee")
                .collect(Collectors.groupingBy(String::length));
        System.out.println("groupingBy: " + byLen);

        int sum = Stream.of(5, 3, 4, 55, 2)
                .mapToInt(Integer::intValue)
                .sum();
        System.out.println("mapToInt.sum: " + sum);

        Stream<String> once = Stream.of("x", "y");
        System.out.println("first terminal: " + once.toList());
        try {
            once.toList();
        } catch (IllegalStateException e) {
            System.out.println("second terminal: " + e.getClass().getSimpleName());
        }
    }
}
