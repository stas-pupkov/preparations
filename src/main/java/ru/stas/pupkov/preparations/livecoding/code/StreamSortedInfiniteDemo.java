package ru.stas.pupkov.preparations.livecoding.code;

import java.util.stream.IntStream;

/**
 * {@code iterate().sorted().limit(5)} зависает: {@code sorted} ждёт весь бесконечный upstream.
 * Рабочий порядок: {@code limit} → {@code sorted}.
 */
public class StreamSortedInfiniteDemo {

    public static void main(String[] args) {
        System.out.print("limit then sorted → ");
        IntStream.iterate(1, x -> x + 1)
                .limit(5)
                .sorted()
                .forEach(System.out::print);
        System.out.println();

        System.out.println("sorted then limit → hang / OOM (не запускаем в demo)");
        // IntStream.iterate(1, x -> x + 1).sorted().limit(5).forEach(System.out::print);
    }
}
