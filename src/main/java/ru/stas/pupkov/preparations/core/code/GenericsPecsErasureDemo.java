package ru.stas.pupkov.preparations.core.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Generics: инвариантность, PECS, type erasure, heap pollution, capture helper.
 */
public class GenericsPecsErasureDemo {

    static void copy(List<? extends Number> src, List<? super Number> dst) {
        for (Number n : src) {
            dst.add(n);
        }
    }

    static void swapFirstTwo(List<?> list) {
        if (list.size() < 2) {
            return;
        }
        swapHelper(list);
    }

    private static <T> void swapHelper(List<T> list) {
        T tmp = list.get(0);
        list.set(0, list.get(1));
        list.set(1, tmp);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void heapPollution() {
        List raw = new ArrayList();
        raw.add(42);
        List<String> strings = raw;
        try {
            String s = strings.get(0);
            System.out.println("unexpected success: " + s);
        } catch (ClassCastException ex) {
            System.out.println("heap pollution fails at read+cast: " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        System.out.println("--- invariance ---");
        List<String> strings = new ArrayList<>();
        strings.add("a");
        // List<Object> objects = strings; // would not compile
        System.out.println("List<String> is not a subtype of List<Object> (compile-time)");

        System.out.println("--- PECS copy ---");
        List<Integer> ints = new ArrayList<>(List.of(1, 2));
        List<Object> objects = new ArrayList<>();
        copy(ints, objects);
        System.out.println("copied into List<Object>: " + objects);

        List<? extends Number> producer = ints;
        Number first = producer.get(0);
        System.out.println("producer.get -> " + first);
        // producer.add(3); // would not compile

        List<? super Integer> consumer = objects;
        consumer.add(3);
        System.out.println("consumer after add: " + consumer);

        System.out.println("--- erasure: same runtime class ---");
        List<String> a = new ArrayList<>();
        List<Integer> b = new ArrayList<>();
        System.out.println("ArrayList<String>.class == ArrayList<Integer>.class: "
                + (a.getClass() == b.getClass()));

        System.out.println("--- wildcard capture helper ---");
        List<String> pair = new ArrayList<>(List.of("left", "right"));
        swapFirstTwo(pair);
        System.out.println("after swap: " + pair);

        System.out.println("--- heap pollution via raw type ---");
        heapPollution();
    }
}
