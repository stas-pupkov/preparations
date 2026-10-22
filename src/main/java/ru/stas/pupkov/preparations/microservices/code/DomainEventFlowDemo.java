package ru.stas.pupkov.preparations.microservices.code;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Сначала commit бизнес-данных, потом реакция на событие (AFTER_COMMIT-идея).
 */
public class DomainEventFlowDemo {

    record OrderPaid(long orderId) { }

    static final class UnitOfWork {
        private boolean committed;
        private final List<Object> events = new ArrayList<>();
        private final List<Consumer<Object>> afterCommit = new ArrayList<>();

        void registerAfterCommit(Consumer<Object> listener) {
            afterCommit.add(listener);
        }

        void pay(long orderId) {
            System.out.println("TX: mark order " + orderId + " PAID in DB");
            events.add(new OrderPaid(orderId));
        }

        void rollback() {
            committed = false;
            events.clear();
            System.out.println("TX: rollback — listeners must NOT run");
        }

        void commit() {
            committed = true;
            System.out.println("TX: commit");
            for (Object event : events) {
                for (Consumer<Object> listener : afterCommit) {
                    listener.accept(event);
                }
            }
        }

        boolean isCommitted() {
            return committed;
        }
    }

    public static void main(String[] args) {
        UnitOfWork ok = new UnitOfWork();
        ok.registerAfterCommit(e -> System.out.println("mail after commit: " + e));
        ok.pay(7);
        ok.commit();

        UnitOfWork fail = new UnitOfWork();
        fail.registerAfterCommit(e -> System.out.println("SHOULD NOT SEE: " + e));
        fail.pay(8);
        fail.rollback();
        System.out.println("committed=" + fail.isCommitted());
    }
}
