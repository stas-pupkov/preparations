package ru.stas.pupkov.preparations.kafka.code;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Модель retry на стороне producer: без идемпотентности повторная отправка после
 * «неясного» ответа даёт дубль в логе; с PID+seq брокер принимает запись один раз.
 */
public class ProducerRetrySemanticsDemo {

    public static void main(String[] args) {
        System.out.println("=== without idempotence: retry after ambiguous ack ===");
        run(/* idempotent= */ false);

        System.out.println("=== with idempotence: same PID+seq rejected as duplicate ===");
        run(/* idempotent= */ true);
    }

    private static void run(boolean idempotent) {
        BrokerLog log = new BrokerLog(idempotent);
        Producer producer = new Producer(log, /* pid= */ 7);

        producer.send("order-42", /* seq= */ 0);
        // Ack «потерялся» по сети: producer не знает, записалось ли — ретраит тот же seq.
        producer.send("order-42", /* seq= */ 0);

        System.out.println("log records: " + log.records());
        System.out.println("unique business payloads: " + new LinkedHashSet<>(log.payloads()));
        System.out.println();
    }

    private static final class Producer {
        private final BrokerLog log;
        private final long pid;

        private Producer(BrokerLog log, long pid) {
            this.log = log;
            this.pid = pid;
        }

        private void send(String payload, int seq) {
            log.append(pid, seq, payload);
        }
    }

    private static final class BrokerLog {
        private final boolean idempotent;
        private final Set<String> seenKeys = new LinkedHashSet<>();
        private final List<String> records = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();

        private BrokerLog(boolean idempotent) {
            this.idempotent = idempotent;
        }

        private void append(long pid, int seq, String payload) {
            String dedupKey = pid + ":" + seq;
            if (idempotent && !seenKeys.add(dedupKey)) {
                System.out.println("broker: duplicate " + dedupKey + " ignored");
                return;
            }
            if (!idempotent) {
                seenKeys.add(dedupKey); // только для наглядности; дедупа нет
            }
            int offset = records.size();
            String record = "offset=" + offset + " payload=" + payload + " (" + dedupKey + ")";
            records.add(record);
            payloads.add(payload);
            System.out.println("broker: appended " + record);
        }

        private List<String> records() {
            return List.copyOf(records);
        }

        private List<String> payloads() {
            return List.copyOf(payloads);
        }
    }
}
