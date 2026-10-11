package ru.stas.pupkov.preparations.core.code;

/**
 * String pool: литералы, {@code new String}, {@code intern()}, constant folding vs runtime concat.
 *
 * <p>HotSpot с Java 7: интернированные {@code String} — в Java heap. StringTable — структура VM;
 * Metaspace хранит metadata классов, не пул строк (PermGen с пулом — до Java 7 / удалён в 8).
 */
public class StringInternPoolDemo {

    public static void main(String[] args) {
        System.out.println("--- literals share pool entry ---");
        String litA = "hello";
        String litB = "hello";
        System.out.println("litA == litB: " + (litA == litB));
        System.out.println("litA.equals(litB): " + litA.equals(litB));

        System.out.println("--- new String(...) is a distinct object ---");
        String heap = new String("hello");
        System.out.println("litA == heap: " + (litA == heap));
        System.out.println("litA.equals(heap): " + litA.equals(heap));

        System.out.println("--- intern() returns pooled instance ---");
        String interned = heap.intern();
        System.out.println("litA == interned: " + (litA == interned));
        System.out.println("heap == interned: " + (heap == interned));

        System.out.println("--- runtime concat is not pooled ---");
        String part = "hel";
        String runtime = part + "lo";
        System.out.println("litA == runtime: " + (litA == runtime));
        System.out.println("litA == runtime.intern(): " + (litA == runtime.intern()));

        System.out.println("--- compile-time constant folding is pooled ---");
        String folded = "hel" + "lo";
        System.out.println("litA == folded: " + (litA == folded));

        System.out.println("--- builder result is not pooled ---");
        String built = new StringBuilder().append("hel").append("lo").toString();
        System.out.println("litA == built: " + (litA == built));
        System.out.println("litA == built.intern(): " + (litA == built.intern()));

        System.out.println("--- distinct content never shares identity ---");
        String other = "hellO";
        System.out.println("litA.equalsIgnoreCase(other): " + litA.equalsIgnoreCase(other));
        System.out.println("litA == other.intern(): " + (litA == other.intern()));

        System.out.println("--- note ---");
        System.out.println("Pooled String objects live in heap (Java 7+); Metaspace != string pool");
    }
}
