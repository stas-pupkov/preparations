package ru.stas.pupkov.preparations.kafka.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель log compaction: для каждого ключа остаётся последнее значение.
 * Tombstone (value=null) помечает ключ к удалению на следующем проходе cleaner'а.
 */
public class LogCompactionModelDemo {

    public static void main(String[] args) {
        List<Entry> log = List.of(
                new Entry("user-1", "v1"),
                new Entry("user-2", "v1"),
                new Entry("user-1", "v2"),
                new Entry("user-3", "v1"),
                new Entry("user-2", null), // tombstone
                new Entry("user-1", "v3")
        );

        System.out.println("raw log:");
        log.forEach(e -> System.out.println("  " + e));

        List<Entry> compacted = compact(log);
        System.out.println("after compaction (tombstones removed in this simplified model):");
        compacted.forEach(e -> System.out.println("  " + e));
    }

    /**
     * Оставляет последнее non-null значение на ключ; tombstone убирает ключ.
     *
     * @param log исходный лог в порядке offset
     * @return    компактное представление «текущего состояния»
     */
    static List<Entry> compact(List<Entry> log) {
        Map<String, String> latest = new LinkedHashMap<>();
        for (Entry entry : log) {
            if (entry.value() == null) {
                latest.remove(entry.key());
            } else {
                latest.put(entry.key(), entry.value());
            }
        }
        List<Entry> result = new ArrayList<>();
        latest.forEach((key, value) -> result.add(new Entry(key, value)));
        return result;
    }

    private record Entry(String key, String value) {
        @Override
        public String toString() {
            return key + "=" + value;
        }
    }
}
