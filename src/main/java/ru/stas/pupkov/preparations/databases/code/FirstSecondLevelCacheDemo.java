package ru.stas.pupkov.preparations.databases.code;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * L1 (per Session) vs L2 (shared): порядок поиска find(id) и зачем query cache нужен L2.
 */
public class FirstSecondLevelCacheDemo {

    static final class EntityData {
        final long id;
        final String name;

        EntityData(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Country{id=" + id + ", name='" + name + "'}";
        }
    }

    /** Disassembled state в L2 — не тот же Java-объект, что в L1. */
    static final class L2Cache {
        private final Map<Long, EntityData> store = new LinkedHashMap<>();

        EntityData get(long id) {
            return store.get(id);
        }

        void put(EntityData data) {
            store.put(data.id, new EntityData(data.id, data.name));
        }
    }

    static final class Session {
        final String name;
        private final Map<Long, EntityData> l1 = new LinkedHashMap<>();
        private final L2Cache l2;
        private final AtomicInteger dbHits;

        Session(String name, L2Cache l2, AtomicInteger dbHits) {
            this.name = name;
            this.l2 = l2;
            this.dbHits = dbHits;
        }

        EntityData find(long id) {
            EntityData fromL1 = l1.get(id);
            if (fromL1 != null) {
                System.out.println(name + " find(" + id + ") → L1 hit, same instance");
                return fromL1;
            }
            EntityData fromL2 = l2.get(id);
            if (fromL2 != null) {
                EntityData managed = new EntityData(fromL2.id, fromL2.name); // hydrate into L1
                l1.put(id, managed);
                System.out.println(name + " find(" + id + ") → L2 hit, hydrate new instance into L1");
                return managed;
            }
            dbHits.incrementAndGet();
            EntityData fromDb = new EntityData(id, "Germany");
            l2.put(fromDb);
            l1.put(id, fromDb);
            System.out.println(name + " find(" + id + ") → DB, put L2 + L1");
            return fromDb;
        }
    }

    public static void main(String[] args) {
        L2Cache sharedL2 = new L2Cache();
        AtomicInteger dbHits = new AtomicInteger();

        System.out.println("=== Session A ===");
        Session a = new Session("A", sharedL2, dbHits);
        EntityData a1 = a.find(1L);
        EntityData a2 = a.find(1L);
        System.out.println("A same instance: " + (a1 == a2));

        System.out.println();
        System.out.println("=== Session B (новый L1, тот же L2) ===");
        Session b = new Session("B", sharedL2, dbHits);
        EntityData b1 = b.find(1L);
        System.out.println("A vs B instances: " + (a1 == b1) + " (разные Session → разные L1 objects)");
        System.out.println("dbHits total=" + dbHits.get() + " (ожидаем 1)");

        System.out.println();
        System.out.println("Query cache хранит id результата; без L2 каждый id снова → DB.");
        System.out.println("L2 уместен на read-mostly; на горячих Order/Balance — stale/инвалидация.");
    }
}
