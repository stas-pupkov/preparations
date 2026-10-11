package ru.stas.pupkov.preparations.collections.code;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Простой LRU-кэш на LinkedHashMap с access-order и removeEldestEntry.
 */
public class LruCacheDemo {

    static final class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxEntries;

        LruCache(int maxEntries) {
            super(16, 0.75f, true);
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxEntries;
        }
    }

    public static void main(String[] args) {
        LruCache<String, Integer> cache = new LruCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        System.out.println("after a,b,c: " + cache.keySet());

        cache.get("a"); // a становится самым «свежим»
        System.out.println("after get(a): " + cache.keySet());

        cache.put("d", 4); // вытеснит наименее используемый (b)
        System.out.println("after put(d): " + cache.keySet());
        System.out.println("contains b? " + cache.containsKey("b"));
        System.out.println("contains a? " + cache.containsKey("a"));
    }
}
