package ru.stas.pupkov.preparations.java8.code;

import java.util.Optional;

/**
 * Контракты Optional: of/ofNullable, orElse vs orElseGet, отказ от слепого get().
 */
public class OptionalPitfallsDemo {

    public static void main(String[] args) {
        Optional<String> present = Optional.of("hello");
        System.out.println("isPresent: " + present.isPresent());
        System.out.println("orElse: " + present.orElse("fallback"));

        Optional<String> absent = Optional.ofNullable(null);
        System.out.println("empty: " + absent.isEmpty());
        System.out.println("orElse on empty: " + absent.orElse("fallback"));

        System.out.println("--- orElse always evaluates argument ---");
        present.orElse(expensiveDefault("orElse/present"));
        absent.orElse(expensiveDefault("orElse/empty"));

        System.out.println("--- orElseGet evaluates only if empty ---");
        present.orElseGet(() -> expensiveDefault("orElseGet/present"));
        absent.orElseGet(() -> expensiveDefault("orElseGet/empty"));

        String mapped = absent.map(String::toUpperCase).orElse("N/A");
        System.out.println("map on empty: " + mapped);

        try {
            Optional.of(null);
        } catch (NullPointerException e) {
            System.out.println("Optional.of(null) -> NPE");
        }

        // anti-pattern: present.get() без проверки; лучше orElseThrow()
        System.out.println("orElseThrow: " + present.orElseThrow());
    }

    private static String expensiveDefault(String label) {
        System.out.println("  expensiveDefault called for " + label);
        return "DEFAULT";
    }
}
