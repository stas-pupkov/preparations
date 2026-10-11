package ru.stas.pupkov.preparations.oop.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Утечка представления через getter vs безопасные варианты.
 */
public class EncapsulationDemo {

    /** Плохо: наружу уходит внутренний мутабельный список. */
    static final class LeakyOrder {
        private final List<String> items = new ArrayList<>();

        void add(String item) {
            items.add(item);
        }

        List<String> getItems() {
            return items;
        }
    }

    /** Лучше: наружу — неизменяемый снимок. */
    static final class SafeOrder {
        private final List<String> items = new ArrayList<>();

        void add(String item) {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("item");
            }
            items.add(item);
        }

        List<String> items() {
            return List.copyOf(items);
        }
    }

    public static void main(String[] args) {
        LeakyOrder leaky = new LeakyOrder();
        leaky.add("book");
        leaky.getItems().add("hacked"); // обошли инвариант класса
        System.out.println("leaky = " + leaky.getItems());

        SafeOrder safe = new SafeOrder();
        safe.add("book");
        try {
            safe.items().add("hacked");
        } catch (UnsupportedOperationException ex) {
            System.out.println("safe rejects external mutation: " + ex.getClass().getSimpleName());
        }
        System.out.println("safe = " + safe.items());
        System.out.println("unmodifiable view example = "
                + Collections.unmodifiableList(List.of("a", "b")));
    }
}
