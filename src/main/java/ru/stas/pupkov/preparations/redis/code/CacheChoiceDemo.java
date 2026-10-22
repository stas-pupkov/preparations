package ru.stas.pupkov.preparations.redis.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Локальный кэш на каждой «ноде» vs общий кэш: рассинхрон после записи.
 */
public class CacheChoiceDemo {

    static final class Node {
        final String name;
        final Map<String, String> local = new ConcurrentHashMap<>();
        final Map<String, String> shared;

        Node(String name, Map<String, String> shared) {
            this.name = name;
            this.shared = shared;
        }

        String getLocal(String key, String fromDb) {
            return local.computeIfAbsent(key, k -> fromDb);
        }

        void putLocal(String key, String value) {
            local.put(key, value);
        }

        String getShared(String key, String fromDb) {
            return shared.computeIfAbsent(key, k -> fromDb);
        }

        void putShared(String key, String value) {
            shared.put(key, value);
        }
    }

    public static void main(String[] args) {
        Map<String, String> redis = new ConcurrentHashMap<>();
        Node a = new Node("A", redis);
        Node b = new Node("B", redis);

        System.out.println("=== local caches ===");
        System.out.println("A sees " + a.getLocal("user:1", "Ann"));
        System.out.println("B sees " + b.getLocal("user:1", "Ann"));
        a.putLocal("user:1", "Ann-v2");
        System.out.println("A updated local → A=" + a.local.get("user:1")
                + ", B still=" + b.local.get("user:1") + " (рассинхрон)");

        System.out.println("=== shared cache ===");
        System.out.println("A sees " + a.getShared("user:2", "Bob"));
        a.putShared("user:2", "Bob-v2");
        System.out.println("B sees same shared=" + b.getShared("user:2", "UNUSED"));
    }
}
