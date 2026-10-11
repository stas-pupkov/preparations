package ru.stas.pupkov.preparations.collections.code;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Мутация ключа после put ломает поиск в HashMap.
 */
public class MutableKeyHashMapDemo {

    static final class MutableKey {
        private int id;

        MutableKey(int id) {
            this.id = id;
        }

        void setId(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MutableKey that)) {
                return false;
            }
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "MutableKey{id=" + id + "}";
        }
    }

    public static void main(String[] args) {
        Map<MutableKey, String> map = new HashMap<>();
        MutableKey key = new MutableKey(1);
        map.put(key, "one");

        System.out.println("before mutate: get(key) = " + map.get(key));
        System.out.println("containsKey: " + map.containsKey(key));

        key.setId(99);
        System.out.println("--- mutated key.id to 99 ---");
        System.out.println("get(same reference) = " + map.get(key));
        System.out.println("containsKey(same reference) = " + map.containsKey(key));
        System.out.println("get(new MutableKey(1)) = " + map.get(new MutableKey(1)));
        System.out.println("map.entrySet() still holds: " + map);
        System.out.println("size still: " + map.size() + " (entry not removed, just hard to find)");
    }
}
