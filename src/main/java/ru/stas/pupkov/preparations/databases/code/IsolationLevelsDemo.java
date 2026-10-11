package ru.stas.pupkov.preparations.databases.code;

import java.util.EnumSet;
import java.util.Set;

/**
 * ANSI-уровни изоляции как набор запрещённых аномалий (без привязки к MVCC/locks).
 */
public class IsolationLevelsDemo {

    enum Anomaly {
        DIRTY_READ,
        NON_REPEATABLE_READ,
        PHANTOM_READ,
        LOST_UPDATE
    }

    enum Level {
        READ_UNCOMMITTED(EnumSet.of(
                Anomaly.DIRTY_READ, Anomaly.NON_REPEATABLE_READ, Anomaly.PHANTOM_READ, Anomaly.LOST_UPDATE)),
        READ_COMMITTED(EnumSet.of(
                Anomaly.NON_REPEATABLE_READ, Anomaly.PHANTOM_READ, Anomaly.LOST_UPDATE)),
        REPEATABLE_READ(EnumSet.of(
                Anomaly.PHANTOM_READ, Anomaly.LOST_UPDATE)),
        SERIALIZABLE(EnumSet.noneOf(Anomaly.class));

        private final Set<Anomaly> stillPossible;

        Level(Set<Anomaly> stillPossible) {
            this.stillPossible = stillPossible;
        }
    }

    public static void main(String[] args) {
        for (Level level : Level.values()) {
            System.out.println(level + " still possible: "
                    + (level.stillPossible.isEmpty() ? "—" : level.stillPossible));
        }
        System.out.println();
        System.out.println("Note: real DBMS may be stricter (e.g. PostgreSQL RR ≈ no phantoms).");
        System.out.println("Lost update is not always listed in ANSI matrix; app-level RMW still needs locks/versions.");
    }
}
