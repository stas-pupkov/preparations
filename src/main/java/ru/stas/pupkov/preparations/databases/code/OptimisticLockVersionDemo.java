package ru.stas.pupkov.preparations.databases.code;

/**
 * Optimistic locking через {@code @Version}: UPDATE ... WHERE id=? AND version=?;
 * 0 updated rows -> OptimisticLockException.
 */
public class OptimisticLockVersionDemo {

    static final class Account {
        final long id;
        long balance;
        long version;

        Account(long id, long balance) {
            this.id = id;
            this.balance = balance;
            this.version = 0;
        }

        @Override
        public String toString() {
            return "Account{id=" + id + ", balance=" + balance + ", version=" + version + "}";
        }
    }

    static final class FakeDbRow {
        long balance;
        long version;

        FakeDbRow(long balance, long version) {
            this.balance = balance;
            this.version = version;
        }
    }

    public static void main(String[] args) {
        FakeDbRow row = new FakeDbRow(100, 0);

        Account tx1 = load(row);
        Account tx2 = load(row);
        System.out.println("Both TX loaded: " + tx1 + " | " + tx2);

        tx1.balance -= 30;
        commit(row, tx1);
        System.out.println("TX1 committed: db=" + row.balance + "@v" + row.version);

        tx2.balance -= 20;
        try {
            commit(row, tx2);
        } catch (OptimisticLockException ex) {
            System.out.println("TX2 failed: " + ex.getMessage());
            System.out.println("Retry strategy: reload, re-apply business op, commit again");
        }
    }

    private static Account load(FakeDbRow row) {
        Account a = new Account(1, row.balance);
        a.version = row.version;
        return a;
    }

    private static void commit(FakeDbRow row, Account entity) {
        System.out.println("SQL: UPDATE account SET balance=" + entity.balance
                + ", version=" + (entity.version + 1)
                + " WHERE id=" + entity.id + " AND version=" + entity.version);
        if (row.version != entity.version) {
            throw new OptimisticLockException(
                    "0 rows updated; expected version=" + entity.version + ", db version=" + row.version);
        }
        row.balance = entity.balance;
        row.version = entity.version + 1;
        entity.version = row.version;
    }

    static final class OptimisticLockException extends RuntimeException {
        OptimisticLockException(String message) {
            super(message);
        }
    }
}
