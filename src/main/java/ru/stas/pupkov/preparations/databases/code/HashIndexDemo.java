package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Учебная модель hash index: ключ → корзина → equality lookup.
 * Диапазон по такому индексу «не умеет» — только полный перебор корзин.
 */
public class HashIndexDemo {

    record Entry(int key, String rowId) {
    }

    public static void main(String[] args) {
        int bucketCount = 8;
        Map<Integer, List<Entry>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < bucketCount; i++) {
            buckets.put(i, new ArrayList<>());
        }

        int[] keys = {10, 18, 26, 11, 42, 7};
        for (int key : keys) {
            int bucket = Math.floorMod(Integer.hashCode(key), bucketCount);
            buckets.get(bucket).add(new Entry(key, "row-" + key));
        }

        System.out.println("=== корзины (hash index) ===");
        buckets.forEach((b, list) -> System.out.println("bucket " + b + ": " + list));

        System.out.println("\n--- WHERE key = 42 (equality) ---");
        System.out.println(lookupEquals(buckets, bucketCount, 42));

        System.out.println("\n--- WHERE key BETWEEN 10 AND 20 — по hash index нельзя seek ---");
        System.out.println("пришлось сканировать все корзины: " + scanRange(buckets, 10, 20));
        System.out.println("(B-tree нашёл бы плотный диапазон в листьях)");
    }

    private static List<Entry> lookupEquals(Map<Integer, List<Entry>> buckets, int bucketCount, int key) {
        int bucket = Math.floorMod(Integer.hashCode(key), bucketCount);
        List<Entry> hit = new ArrayList<>();
        for (Entry e : buckets.get(bucket)) {
            if (e.key() == key) {
                hit.add(e);
            }
        }
        System.out.println("смотрели только bucket " + bucket);
        return hit;
    }

    private static List<Entry> scanRange(Map<Integer, List<Entry>> buckets, int from, int to) {
        List<Entry> hit = new ArrayList<>();
        for (List<Entry> list : buckets.values()) {
            for (Entry e : list) {
                if (e.key() >= from && e.key() <= to) {
                    hit.add(e);
                }
            }
        }
        return hit;
    }
}
