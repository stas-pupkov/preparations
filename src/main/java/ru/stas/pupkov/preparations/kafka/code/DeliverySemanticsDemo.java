package ru.stas.pupkov.preparations.kafka.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель at-most-once / at-least-once через момент commit offset относительно обработки.
 * Живой брокер не нужен: важно увидеть, где появляются потери и дубликаты.
 */
public class DeliverySemanticsDemo {

    public static void main(String[] args) {
        List<String> messages = List.of("A", "B", "C");

        System.out.println("=== at-most-once: commit before process ===");
        run(messages, CommitMoment.BEFORE_PROCESS, /* crashOn= */ "B");

        System.out.println("=== at-least-once: commit after process ===");
        run(messages, CommitMoment.AFTER_PROCESS, /* crashOn= */ "B");
    }

    private static void run(List<String> messages, CommitMoment moment, String crashOn) {
        InMemoryPartition partition = new InMemoryPartition(messages);
        Consumer consumer = new Consumer(partition);
        List<String> processed = new ArrayList<>();

        try {
            while (consumer.hasNext()) {
                String msg = consumer.pollNext();
                if (moment == CommitMoment.BEFORE_PROCESS) {
                    consumer.commit();
                }
                if (msg.equals(crashOn)) {
                    throw new SimulatedCrash("crash while handling " + msg);
                }
                processed.add(msg);
                if (moment == CommitMoment.AFTER_PROCESS) {
                    consumer.commit();
                }
            }
        } catch (SimulatedCrash crash) {
            System.out.println("crash: " + crash.getMessage());
        }

        System.out.println("processed before crash: " + processed);
        System.out.println("committed offset (next to read): " + consumer.committedOffset());

        // «Новый» consumer той же группы поднимается с committed offset.
        Consumer restarted = new Consumer(partition, consumer.committedOffset());
        List<String> afterRestart = new ArrayList<>();
        while (restarted.hasNext()) {
            afterRestart.add(restarted.pollNext());
            restarted.commit();
        }
        System.out.println("processed after restart: " + afterRestart);
        System.out.println();
    }

    private enum CommitMoment {
        BEFORE_PROCESS,
        AFTER_PROCESS
    }

    private static final class InMemoryPartition {
        private final List<String> messages;

        private InMemoryPartition(List<String> messages) {
            this.messages = List.copyOf(messages);
        }

        private String get(int offset) {
            return messages.get(offset);
        }

        private int size() {
            return messages.size();
        }
    }

    private static final class Consumer {
        private final InMemoryPartition partition;
        private int position;
        private int committedOffset;

        private Consumer(InMemoryPartition partition) {
            this(partition, 0);
        }

        private Consumer(InMemoryPartition partition, int committedOffset) {
            this.partition = partition;
            this.committedOffset = committedOffset;
            this.position = committedOffset;
        }

        private boolean hasNext() {
            return position < partition.size();
        }

        private String pollNext() {
            return partition.get(position++);
        }

        private void commit() {
            // В Kafka commit хранит следующий offset для чтения.
            committedOffset = position;
        }

        private int committedOffset() {
            return committedOffset;
        }
    }

    private static final class SimulatedCrash extends RuntimeException {
        private SimulatedCrash(String message) {
            super(message);
        }
    }
}
