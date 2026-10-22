package ru.stas.pupkov.preparations.livecoding.code;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code HashSet.toString()} не гарантирует порядок вставки; {@code LinkedHashSet} — гарантирует.
 */
public class HashSetPrintOrderDemo {

    public static void main(String[] args) {
        Set<String> hash = new HashSet<>(Arrays.asList("Сталин", "Ленин", "Че Гевара"));
        Set<String> linked = new LinkedHashSet<>(Arrays.asList("Сталин", "Ленин", "Че Гевара"));

        System.out.println("Leaders = " + hash);
        System.out.println("LinkedHashSet (insertion order) = " + linked);
        System.out.println("hash size=" + hash.size() + " (все три уникальны)");
    }
}
