package ru.stas.pupkov.preparations.collections.code;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HashMap сравнивает ключи через equals/hashCode; IdentityHashMap — через ==.
 */
public class IdentityHashMapDemo {

    static final class Name {
        private final String value;

        Name(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Name name && Objects.equals(value, name.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "Name(" + value + ")@" + Integer.toHexString(System.identityHashCode(this));
        }
    }

    public static void main(String[] args) {
        Name a = new Name("Ada");
        Name b = new Name("Ada"); // equals == true, но другая ссылка

        Map<Name, String> hashMap = new HashMap<>();
        Map<Name, String> identityMap = new IdentityHashMap<>();

        hashMap.put(a, "from-a");
        identityMap.put(a, "from-a");

        System.out.println("a.equals(b) = " + a.equals(b));
        System.out.println("a == b = " + (a == b));
        System.out.println("HashMap.get(b) = " + hashMap.get(b));
        System.out.println("IdentityHashMap.get(b) = " + identityMap.get(b));
        System.out.println("IdentityHashMap.get(a) = " + identityMap.get(a));

        identityMap.put(b, "from-b");
        System.out.println("IdentityHashMap size after put(b): " + identityMap.size());
        System.out.println("HashMap size if put(b): would stay 1 because equals");
        hashMap.put(b, "from-b");
        System.out.println("HashMap size after put(b): " + hashMap.size());
    }
}
