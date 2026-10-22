package ru.stas.pupkov.preparations.kafka.code;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Модель дефолтного партиционирования по ключу: hash(key) % numPartitions.
 * Показывает, почему порядок гарантирован только внутри партиции и только для
 * сообщений с одним ключом (при неизменном числе партиций).
 */
public class KeyBasedPartitioningDemo {

    public static void main(String[] args) {
        int partitions = 3;
        List<Record> events = List.of(
                new Record("acc-1", "deposit:500"),
                new Record("acc-1", "withdraw:500"),
                new Record("acc-2", "deposit:100"),
                new Record("acc-1", "deposit:50"),
                new Record("acc-2", "withdraw:20"),
                new Record(null, "metric:cpu")
        );

        Map<Integer, List<Record>> byPartition = new LinkedHashMap<>();
        for (int p = 0; p < partitions; p++) {
            byPartition.put(p, new ArrayList<>());
        }

        for (Record event : events) {
            int partition = partitionFor(event.key(), partitions);
            byPartition.get(partition).add(event);
            System.out.printf("key=%s -> partition=%d value=%s%n",
                    event.key(), partition, event.value());
        }

        System.out.println("--- per-partition order (as a single consumer would read) ---");
        byPartition.forEach((partition, records) -> {
            System.out.println("partition " + partition + ":");
            for (int i = 0; i < records.size(); i++) {
                Record r = records.get(i);
                System.out.printf("  offset=%d key=%s value=%s%n", i, r.key(), r.value());
            }
        });

        System.out.println("--- after increasing partitions (same keys can move) ---");
        int morePartitions = 4;
        for (String key : List.of("acc-1", "acc-2")) {
            System.out.printf("key=%s: p=%d -> p=%d%n",
                    key, partitionFor(key, partitions), partitionFor(key, morePartitions));
        }
    }

    /**
     * Упрощённый аналог murmur2 % n из Kafka DefaultPartitioner / BuiltInPartitioner.
     * Для демо достаточно стабильного hash — важна идея, не бит-в-бит совместимость.
     *
     * @param key            ключ сообщения; {@code null} — round-robin stub
     * @param numPartitions  число партиций топика
     * @return               номер партиции
     */
    static int partitionFor(String key, int numPartitions) {
        if (key == null) {
            // Без ключа клиент выбирает партицию иначе (sticky / round-robin) —
            // здесь фиксируем «свалку» в последнюю партицию для наглядности.
            return numPartitions - 1;
        }
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        int hash = murmur2(bytes);
        return toPositive(hash) % numPartitions;
    }

    /** Упрощённый murmur2 как в org.apache.kafka.common.utils.Utils.murmur2. */
    private static int murmur2(byte[] data) {
        int length = data.length;
        int seed = 0x9747b28c;
        int m = 0x5bd1e995;
        int r = 24;

        int h = seed ^ length;
        int length4 = length / 4;

        for (int i = 0; i < length4; i++) {
            int i4 = i * 4;
            int k = (data[i4] & 0xff)
                    | ((data[i4 + 1] & 0xff) << 8)
                    | ((data[i4 + 2] & 0xff) << 16)
                    | ((data[i4 + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        int remainder = length % 4;
        if (remainder == 3) {
            h ^= (data[(length & ~3) + 2] & 0xff) << 16;
        }
        if (remainder >= 2) {
            h ^= (data[(length & ~3) + 1] & 0xff) << 8;
        }
        if (remainder >= 1) {
            h ^= data[length & ~3] & 0xff;
            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h;
    }

    private static int toPositive(int number) {
        return number & 0x7fffffff;
    }

    private record Record(String key, String value) {
        Record {
            Objects.requireNonNull(value);
        }
    }
}
