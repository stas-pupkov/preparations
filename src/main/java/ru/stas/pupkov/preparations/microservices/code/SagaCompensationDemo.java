package ru.stas.pupkov.preparations.microservices.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestration-saga: шаги локальных tx + компенсации при сбое.
 */
public class SagaCompensationDemo {

    enum Step { RESERVE, CHARGE, CONFIRM }

    static final class SagaLog {
        final List<Step> done = new ArrayList<>();
        final List<String> compensations = new ArrayList<>();
    }

    static final class OrderSaga {
        private final boolean paymentOk;

        OrderSaga(boolean paymentOk) {
            this.paymentOk = paymentOk;
        }

        SagaLog run(String orderId) {
            SagaLog log = new SagaLog();
            try {
                reserve(orderId, log);
                charge(orderId, log);
                confirm(orderId, log);
            } catch (RuntimeException ex) {
                System.out.println("fail: " + ex.getMessage() + " → compensate");
                compensate(log);
            }
            return log;
        }

        private void reserve(String orderId, SagaLog log) {
            System.out.println("  reserve stock for " + orderId);
            log.done.add(Step.RESERVE);
        }

        private void charge(String orderId, SagaLog log) {
            System.out.println("  charge card for " + orderId);
            if (!paymentOk) {
                throw new IllegalStateException("payment declined");
            }
            log.done.add(Step.CHARGE);
        }

        private void confirm(String orderId, SagaLog log) {
            System.out.println("  confirm order " + orderId);
            log.done.add(Step.CONFIRM);
        }

        private void compensate(SagaLog log) {
            for (int i = log.done.size() - 1; i >= 0; i--) {
                Step step = log.done.get(i);
                String action = switch (step) {
                    case CONFIRM -> "cancel order";
                    case CHARGE -> "refund";
                    case RESERVE -> "release stock";
                };
                log.compensations.add(action);
                System.out.println("  compensate: " + action);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== happy path ===");
        SagaLog ok = new OrderSaga(true).run("ord-1");
        System.out.println("steps=" + ok.done + ", compensations=" + ok.compensations);

        System.out.println("=== payment fails ===");
        SagaLog fail = new OrderSaga(false).run("ord-2");
        System.out.println("steps=" + fail.done + ", compensations=" + fail.compensations);
    }
}
