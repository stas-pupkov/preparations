package ru.stas.pupkov.preparations.databases.code;

import java.util.HashMap;
import java.util.Map;

/**
 * Упрощённая модель atomicity: изменения видны снаружи только после commit.
 */
public class TransactionAcidityDemo {

    static final class Ledger {
        private final Map<String, Integer> balances = new HashMap<>();

        Ledger() {
            balances.put("Alice", 100);
            balances.put("Bob", 100);
        }

        int balance(String account) {
            return balances.getOrDefault(account, 0);
        }

        Tx begin() {
            return new Tx(this);
        }

        private void apply(Map<String, Integer> dirty) {
            balances.putAll(dirty);
        }
    }

    static final class Tx {
        private final Ledger ledger;
        private final Map<String, Integer> dirty = new HashMap<>();
        private boolean open = true;

        Tx(Ledger ledger) {
            this.ledger = ledger;
        }

        void transfer(String from, String to, int amount) {
            ensureOpen();
            int fromBal = dirty.getOrDefault(from, ledger.balance(from)) - amount;
            int toBal = dirty.getOrDefault(to, ledger.balance(to)) + amount;
            if (fromBal < 0) {
                throw new IllegalStateException("insufficient funds");
            }
            dirty.put(from, fromBal);
            dirty.put(to, toBal);
        }

        void commit() {
            ensureOpen();
            ledger.apply(dirty);
            open = false;
        }

        void rollback() {
            ensureOpen();
            dirty.clear();
            open = false;
        }

        private void ensureOpen() {
            if (!open) {
                throw new IllegalStateException("tx closed");
            }
        }
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        System.out.println("start: Alice=" + ledger.balance("Alice") + " Bob=" + ledger.balance("Bob"));

        Tx ok = ledger.begin();
        ok.transfer("Alice", "Bob", 40);
        System.out.println("before commit (outside): Alice=" + ledger.balance("Alice"));
        ok.commit();
        System.out.println("after commit: Alice=" + ledger.balance("Alice") + " Bob=" + ledger.balance("Bob"));

        Tx fail = ledger.begin();
        try {
            fail.transfer("Alice", "Bob", 1000);
            fail.commit();
        } catch (IllegalStateException e) {
            fail.rollback();
            System.out.println("rollback after error: " + e.getMessage());
        }
        System.out.println("final: Alice=" + ledger.balance("Alice") + " Bob=" + ledger.balance("Bob"));
    }
}
