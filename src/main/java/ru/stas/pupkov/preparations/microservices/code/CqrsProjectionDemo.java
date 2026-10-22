package ru.stas.pupkov.preparations.microservices.code;

import java.util.HashMap;
import java.util.Map;

/**
 * CQRS: write-модель меняется командой, read-модель обновляется проекцией события.
 */
public class CqrsProjectionDemo {

    record PlaceOrder(String orderId, String userId, int total) { }

    record OrderPlaced(String orderId, String userId, int total) { }

    /** Нормализованное хранилище команд. */
    static final class WriteStore {
        private final Map<String, OrderPlaced> orders = new HashMap<>();

        OrderPlaced handle(PlaceOrder cmd) {
            OrderPlaced event = new OrderPlaced(cmd.orderId(), cmd.userId(), cmd.total());
            orders.put(cmd.orderId(), event);
            return event;
        }
    }

    /** Денормализованный вид для UI. */
    static final class ReadStore {
        private final Map<String, String> views = new HashMap<>();

        void on(OrderPlaced event) {
            views.put(event.orderId(),
                    "user=" + event.userId() + ", total=" + event.total() + ", status=PLACED");
        }

        String get(String orderId) {
            return views.getOrDefault(orderId, "<empty>");
        }
    }

    public static void main(String[] args) {
        WriteStore write = new WriteStore();
        ReadStore read = new ReadStore();

        System.out.println("before projection: " + read.get("o-1"));
        OrderPlaced event = write.handle(new PlaceOrder("o-1", "u-9", 500));
        // в жизни — через брокер; здесь синхронно для ясности
        read.on(event);
        System.out.println("after projection:  " + read.get("o-1"));
        System.out.println("write side knows event: " + event);
    }
}
