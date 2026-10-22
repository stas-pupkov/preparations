package ru.stas.pupkov.preparations.kafka.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель назначения партиций внутри одной consumer group (range-подобное деление).
 * Показывает idle consumers при consumers > partitions и независимость групп.
 */
public class ConsumerGroupAssignmentDemo {

    public static void main(String[] args) {
        int partitions = 3;

        System.out.println("=== one group, consumers < partitions ===");
        printAssignment("analytics", partitions, List.of("c1", "c2"));

        System.out.println("=== one group, consumers > partitions ===");
        printAssignment("analytics", partitions, List.of("c1", "c2", "c3", "c4"));

        System.out.println("=== two independent groups on same topic ===");
        printAssignment("analytics", partitions, List.of("a1", "a2"));
        printAssignment("billing", partitions, List.of("b1"));
    }

    private static void printAssignment(String groupId, int partitions, List<String> consumers) {
        Map<String, List<Integer>> assignment = assign(partitions, consumers);
        System.out.println("group=" + groupId);
        assignment.forEach((consumer, parts) -> {
            if (parts.isEmpty()) {
                System.out.println("  " + consumer + " -> IDLE");
            } else {
                System.out.println("  " + consumer + " -> partitions " + parts);
            }
        });
        System.out.println();
    }

    /**
     * Упрощённый RangeAssignor: партиции делятся по порядку между отсортированными consumer id.
     *
     * @param partitions число партиций топика
     * @param consumers  id консьюмеров в группе
     * @return           consumer id → список партиций
     */
    static Map<String, List<Integer>> assign(int partitions, List<String> consumers) {
        List<String> sorted = consumers.stream().sorted().toList();
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (String consumer : sorted) {
            result.put(consumer, new ArrayList<>());
        }
        if (sorted.isEmpty() || partitions <= 0) {
            return result;
        }

        int base = partitions / sorted.size();
        int remainder = partitions % sorted.size();
        int nextPartition = 0;
        for (int i = 0; i < sorted.size(); i++) {
            int count = base + (i < remainder ? 1 : 0);
            List<Integer> assigned = result.get(sorted.get(i));
            for (int j = 0; j < count; j++) {
                assigned.add(nextPartition++);
            }
        }
        return result;
    }
}
