package ru.stas.pupkov.preparations.java8.code;

import java.util.HashMap;
import java.util.Map;

/**
 * computeIfAbsent, merge, compute с возвратом null (удаление mapping).
 */
public class MapApiJava8Demo {

    public static void main(String[] args) {
        Map<String, Integer> counts = new HashMap<>();
        counts.merge("a", 1, Integer::sum);
        counts.merge("a", 1, Integer::sum);
        counts.merge("b", 1, Integer::sum);
        System.out.println("merge counts: " + counts);

        Map<String, String> cache = new HashMap<>();
        String loaded = cache.computeIfAbsent("user:1", key -> {
            System.out.println("  loading " + key);
            return "Alice";
        });
        String cached = cache.computeIfAbsent("user:1", key -> {
            System.out.println("  loading again (should not happen)");
            return "Bob";
        });
        System.out.println("computeIfAbsent: " + loaded + ", " + cached + ", map=" + cache);

        cache.compute("user:1", (k, v) -> null);
        System.out.println("compute -> null removes entry: " + cache);

        Map<String, String> map = new HashMap<>();
        map.put("a", "A");
        System.out.println("getOrDefault missing: " + map.getOrDefault("z", "n/a"));
        map.putIfAbsent("a", "IGNORED");
        map.putIfAbsent("b", "B");
        System.out.println("putIfAbsent: " + map);
    }
}
