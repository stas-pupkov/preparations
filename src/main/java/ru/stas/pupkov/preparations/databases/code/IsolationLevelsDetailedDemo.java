package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Пошаговые таймлайны ANSI-уровней изоляции на учебной in-memory модели.
 *
 * <p>Моделирует явления (phenomena), а не байт-в-байт PostgreSQL/InnoDB.
 */
public class IsolationLevelsDetailedDemo {

    public static void main(String[] args) {
        demoReadUncommittedDirtyRead();
        demoReadCommittedNonRepeatableAndPhantom();
        demoRepeatableRead();
        demoSerializable();
        demoLostUpdateUnderReadCommitted();
    }

    private static void demoReadUncommittedDirtyRead() {
        header("1) READ UNCOMMITTED — dirty read");
        explain("""
                Смысл уровня: можно читать чужие ещё не committed изменения.
                Риск: решение принято на данных, которые потом откатятся.
                На практике почти не используют: RC даёт ту же throughput без dirty read.
                """);

        Database db = Database.with(account(1, 100));
        Tx t1 = db.begin(Isolation.READ_UNCOMMITTED);
        Tx t2 = db.begin(Isolation.READ_UNCOMMITTED);

        step("T1", "UPDATE accounts SET balance=999 WHERE id=1  -- без COMMIT");
        t1.write(1, 999);

        step("T2", "SELECT balance FROM accounts WHERE id=1");
        System.out.println("    -> " + t2.read(1) + "   << dirty read: увидели незакоммиченное 999");

        step("T1", "ROLLBACK");
        t1.rollback();

        step("T2", "SELECT balance FROM accounts WHERE id=1");
        System.out.println("    -> " + t2.read(1) + "   << «правда» снова 100; любые расчёты на 999 уже ложны");
        System.out.println();
    }

    private static void demoReadCommittedNonRepeatableAndPhantom() {
        header("2) READ COMMITTED — нет dirty read; возможны non-repeatable и phantom");
        explain("""
                Смысл: каждый statement видит только committed данные (плюс свои writes).
                В PostgreSQL — новый snapshot на каждый statement.
                Default во многих СУБД (PG, Oracle). Подходит для большинства OLTP,
                если инварианты защищены атомарным UPDATE / locks / version.
                """);

        Database db = Database.with(account(1, 100), account(2, 200));
        Tx t1 = db.begin(Isolation.READ_COMMITTED);
        Tx t2 = db.begin(Isolation.READ_COMMITTED);

        step("T1", "SELECT balance WHERE id=1");
        System.out.println("    -> " + t1.read(1));

        step("T2", "UPDATE id=1 SET balance=40; COMMIT");
        t2.write(1, 40);
        t2.commit();

        step("T1", "SELECT balance WHERE id=1   -- тот же SELECT в той же tx");
        System.out.println("    -> " + t1.read(1) + "   << non-repeatable read: committed изменение T2 стало видно");

        step("T1", "SELECT id WHERE balance >= 100");
        System.out.println("    -> ids=" + t1.readIds(a -> a.balance >= 100));

        Tx t3 = db.begin(Isolation.READ_COMMITTED);
        step("T3", "INSERT (id=3, balance=150); COMMIT");
        t3.insert(3, 150);
        t3.commit();

        step("T1", "SELECT id WHERE balance >= 100   -- повтор predicate-read");
        System.out.println("    -> ids=" + t1.readIds(a -> a.balance >= 100)
                + "   << phantom: в результат добавилась новая строка");
        t1.rollback();
        System.out.println();
    }

    private static void demoRepeatableRead() {
        header("3) REPEATABLE READ — стабильные строки; ANSI всё ещё допускает phantom");
        explain("""
                Смысл ANSI: повторное чтение УЖЕ ПРОЧИТАННОЙ строки даёт то же значение.
                Фантомы (новые/исчезнувшие строки по предикату) стандартом при RR не запрещены.
                Реализации строже стандарта:
                  • PostgreSQL RR ≈ snapshot на всю tx → типичный phantom не виден;
                  • MySQL InnoDB RR + gap/next-key locks на locking reads/writes.
                """);

        Database db = Database.with(account(1, 100), account(2, 200));
        Tx t1 = db.begin(Isolation.REPEATABLE_READ);
        Tx t2 = db.begin(Isolation.READ_COMMITTED);

        step("T1", "SELECT balance WHERE id=1");
        System.out.println("    -> " + t1.read(1));

        step("T2", "UPDATE id=1 SET balance=1; COMMIT");
        t2.write(1, 1);
        t2.commit();

        step("T1", "SELECT balance WHERE id=1 again");
        System.out.println("    -> " + t1.read(1) + "   << non-repeatable закрыт (видим снимок начала T1)");

        step("T1", "SELECT id WHERE balance >= 100");
        List<Integer> first = t1.readIds(a -> a.balance >= 100);
        System.out.println("    -> ids=" + first);

        Tx t3 = db.begin(Isolation.READ_COMMITTED);
        step("T3", "INSERT (id=3, balance=150); COMMIT");
        t3.insert(3, 150);
        t3.commit();

        step("T1", "SELECT id WHERE balance >= 100 again");
        List<Integer> second = t1.readIds(a -> a.balance >= 100);
        // В этой учебной модели RR = snapshot на tx, поэтому phantom НЕ появится —
        // как в PostgreSQL. Явно печатаем оба факта.
        System.out.println("    -> ids=" + second);
        if (second.equals(first)) {
            System.out.println("    << в snapshot-модели (как PG RR) phantom не виден");
            System.out.println("    << в «чистом ANSI RR» на locking-реализации без gap-locks second мог бы стать "
                    + concat(first, 3));
        }
        t1.rollback();
        System.out.println();
    }

    private static void demoSerializable() {
        header("4) SERIALIZABLE — эквивалентность некоторому последовательному порядку");
        explain("""
                Смысл: итог concurrent-выполнения = как будто транзакции шли по одной.
                Закрывает dirty / non-repeatable / phantom (и многие write-skew сценарии).
                Цена: больше блокировок или abort'ы (в PostgreSQL SSI → serialization_failure),
                клиент ОБЯЗАН ретраить транзакцию.
                """);

        Database db = Database.with(account(1, 100), account(2, 200));
        Tx t1 = db.begin(Isolation.SERIALIZABLE);

        step("T1", "SELECT SUM(balance) WHERE balance >= 100");
        int sum1 = t1.sum(a -> a.balance >= 100);
        System.out.println("    -> sum=" + sum1 + "  (T1 зафиксировала predicate read-set)");

        Tx t2 = db.begin(Isolation.READ_COMMITTED);
        step("T2", "INSERT (id=3, balance=150); COMMIT");
        try {
            t2.insert(3, 150);
            t2.commit();
            System.out.println("    -> T2 committed");
        } catch (SerializationFailure e) {
            System.out.println("    -> T2 aborted: " + e.getMessage());
        }

        step("T1", "SELECT SUM(balance) WHERE balance >= 100");
        int sum2 = t1.sum(a -> a.balance >= 100);
        System.out.println("    -> sum=" + sum2 + "   << для T1 результат стабилен (нет phantom)");

        step("T1", "COMMIT");
        try {
            t1.commit();
            System.out.println("    -> T1 committed");
        } catch (SerializationFailure e) {
            System.out.println("    -> T1 aborted (SSI может выбрать любую сторону конфликта): " + e.getMessage());
        }
        System.out.println();
    }

    private static void demoLostUpdateUnderReadCommitted() {
        header("5) Даже на READ COMMITTED — lost update при read-modify-write");
        explain("""
                Матрица ANSI про dirty/non-repeatable/phantom НЕ закрывает классический RMW:
                обе tx читают 0, обе пишут 1, итог 1 вместо 2.
                Isolation level ≠ автоматическая безопасность бизнес-инварианта.
                """);

        Database db = Database.with(account(1, 0));
        Tx t1 = db.begin(Isolation.READ_COMMITTED);
        Tx t2 = db.begin(Isolation.READ_COMMITTED);

        step("T1", "v = SELECT balance");
        int v1 = t1.read(1);
        step("T2", "v = SELECT balance");
        int v2 = t2.read(1);
        step("T1", "UPDATE SET balance=v+1 (" + (v1 + 1) + "); COMMIT");
        t1.write(1, v1 + 1);
        t1.commit();
        step("T2", "UPDATE SET balance=v+1 (" + (v2 + 1) + "); COMMIT");
        t2.write(1, v2 + 1);
        t2.commit();

        System.out.println("    final=" + db.begin(Isolation.READ_COMMITTED).read(1)
                + "   << ожидали 2; нужно UPDATE … SET balance=balance+1 / FOR UPDATE / version");
        System.out.println();
    }

    // ----------------- модель -----------------

    enum Isolation {
        READ_UNCOMMITTED,
        READ_COMMITTED,
        REPEATABLE_READ,
        SERIALIZABLE
    }

    static final class Account {
        final int id;
        int balance;

        Account(int id, int balance) {
            this.id = id;
            this.balance = balance;
        }

        Account copy() {
            return new Account(id, balance);
        }
    }

    static Account account(int id, int balance) {
        return new Account(id, balance);
    }

    static final class Database {
        private final Map<Integer, Account> committed = new LinkedHashMap<>();
        private final List<Tx> open = new CopyOnWriteArrayList<>();
        private final List<Tx> predicateReaders = new CopyOnWriteArrayList<>();

        static Database with(Account... accounts) {
            Database db = new Database();
            for (Account account : accounts) {
                db.committed.put(account.id, account.copy());
            }
            return db;
        }

        Tx begin(Isolation isolation) {
            Map<Integer, Account> snap = snapshot();
            Tx tx = new Tx(this, isolation, snap);
            open.add(tx);
            if (isolation == Isolation.REPEATABLE_READ || isolation == Isolation.SERIALIZABLE) {
                predicateReaders.add(tx);
            }
            return tx;
        }

        Map<Integer, Account> snapshot() {
            Map<Integer, Account> snap = new LinkedHashMap<>();
            committed.forEach((id, acc) -> snap.put(id, acc.copy()));
            return snap;
        }

        void commit(Tx tx) {
            // SSI-подобно: insert под живым SERIALIZABLE reader с predicate → abort писателя
            boolean conflictsWithSerializableReader = tx.hasInserts()
                    && predicateReaders.stream().anyMatch(r -> r != tx && r.open
                    && r.isolation == Isolation.SERIALIZABLE);
            if (conflictsWithSerializableReader) {
                close(tx);
                throw new SerializationFailure(
                        "serialization_failure: insert conflicts with concurrent SERIALIZABLE read set");
            }
            for (Account write : tx.writes.values()) {
                committed.put(write.id, write.copy());
            }
            close(tx);
        }

        void close(Tx tx) {
            tx.open = false;
            open.remove(tx);
            predicateReaders.remove(tx);
        }
    }

    static final class Tx {
        private final Database db;
        final Isolation isolation;
        private final Map<Integer, Account> snapshotAtStart;
        final Map<Integer, Account> writes = new LinkedHashMap<>();
        boolean open = true;

        Tx(Database db, Isolation isolation, Map<Integer, Account> snapshotAtStart) {
            this.db = db;
            this.isolation = isolation;
            this.snapshotAtStart = snapshotAtStart;
        }

        int read(int id) {
            ensureOpen();
            return visible().get(id).balance;
        }

        List<Integer> readIds(Predicate<Account> predicate) {
            ensureOpen();
            return visible().values().stream().filter(predicate).map(a -> a.id).sorted().toList();
        }

        int sum(Predicate<Account> predicate) {
            ensureOpen();
            return visible().values().stream().filter(predicate).mapToInt(a -> a.balance).sum();
        }

        void write(int id, int balance) {
            ensureOpen();
            Account row = visible().get(id).copy();
            row.balance = balance;
            writes.put(id, row);
        }

        void insert(int id, int balance) {
            ensureOpen();
            if (db.committed.containsKey(id) || writes.containsKey(id)) {
                throw new IllegalStateException("duplicate id " + id);
            }
            writes.put(id, new Account(id, balance));
        }

        boolean hasInserts() {
            return writes.keySet().stream().anyMatch(id -> !snapshotAtStart.containsKey(id));
        }

        void commit() {
            ensureOpen();
            db.commit(this);
        }

        void rollback() {
            ensureOpen();
            writes.clear();
            db.close(this);
        }

        private Map<Integer, Account> visible() {
            Map<Integer, Account> base = switch (isolation) {
                case READ_UNCOMMITTED -> {
                    Map<Integer, Account> m = db.snapshot();
                    for (Tx other : db.open) {
                        if (other != this && other.open) {
                            other.writes.forEach((id, acc) -> m.put(id, acc.copy()));
                        }
                    }
                    yield m;
                }
                case READ_COMMITTED -> db.snapshot();
                case REPEATABLE_READ, SERIALIZABLE -> copyMap(snapshotAtStart);
            };
            writes.forEach((id, acc) -> base.put(id, acc.copy()));
            return base;
        }

        private static Map<Integer, Account> copyMap(Map<Integer, Account> src) {
            Map<Integer, Account> m = new LinkedHashMap<>();
            src.forEach((id, acc) -> m.put(id, acc.copy()));
            return m;
        }

        private void ensureOpen() {
            if (!open) {
                throw new IllegalStateException("tx closed");
            }
        }
    }

    static final class SerializationFailure extends RuntimeException {
        SerializationFailure(String message) {
            super(message);
        }
    }

    private static List<Integer> concat(List<Integer> base, int extra) {
        List<Integer> out = new ArrayList<>(base);
        out.add(extra);
        return out;
    }

    private static void header(String title) {
        System.out.println("======== " + title + " ========");
    }

    private static void explain(String text) {
        text.lines().filter(l -> !l.isBlank()).forEach(l -> System.out.println("  " + l.strip()));
        System.out.println();
    }

    private static void step(String who, String sql) {
        System.out.println(who + ": " + sql);
    }
}
