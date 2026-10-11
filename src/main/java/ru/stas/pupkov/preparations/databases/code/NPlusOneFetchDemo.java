package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Классический N+1: один SELECT родителей + N SELECT коллекций при lazy-доступе в цикле.
 */
public class NPlusOneFetchDemo {

    record Order(long id, String customer) {
    }

    record Item(long orderId, String sku) {
    }

    static final class SqlLog {
        final List<String> sql = new ArrayList<>();

        void log(String statement) {
            sql.add(statement);
            System.out.println("SQL: " + statement);
        }

        int count() {
            return sql.size();
        }
    }

    static final class FakeDb {
        final Map<Long, Order> orders = new LinkedHashMap<>();
        final Map<Long, List<Item>> itemsByOrder = new LinkedHashMap<>();

        FakeDb() {
            orders.put(1L, new Order(1, "Alice"));
            orders.put(2L, new Order(2, "Bob"));
            orders.put(3L, new Order(3, "Carol"));
            itemsByOrder.put(1L, List.of(new Item(1, "A"), new Item(1, "B")));
            itemsByOrder.put(2L, List.of(new Item(2, "C")));
            itemsByOrder.put(3L, List.of(new Item(3, "D"), new Item(3, "E"), new Item(3, "F")));
        }
    }

    public static void main(String[] args) {
        FakeDb db = new FakeDb();

        System.out.println("=== N+1 (LAZY collection touched in loop) ===");
        SqlLog n1 = new SqlLog();
        List<Order> page = loadOrders(db, n1);
        for (Order order : page) {
            List<Item> items = loadItemsForOrder(db, order.id(), n1); // +1 per parent
            System.out.println(order.customer() + " -> " + items.size() + " items");
        }
        System.out.println("Total SQL: " + n1.count() + " (1 + N)");

        System.out.println();
        System.out.println("=== Fix: join fetch / one query for children ===");
        SqlLog fixed = new SqlLog();
        Map<Long, List<Item>> graph = loadOrdersWithItems(db, fixed);
        graph.forEach((id, items) ->
                System.out.println(db.orders.get(id).customer() + " -> " + items.size() + " items"));
        System.out.println("Total SQL: " + fixed.count());

        System.out.println();
        System.out.println("=== Fix: @BatchSize style IN (...) ===");
        SqlLog batch = new SqlLog();
        List<Order> orders = loadOrders(db, batch);
        loadItemsBatched(db, orders.stream().map(Order::id).toList(), batch);
        System.out.println("Total SQL: " + batch.count() + " (1 parents + 1 batched children)");
    }

    private static List<Order> loadOrders(FakeDb db, SqlLog log) {
        log.log("SELECT * FROM orders");
        return List.copyOf(db.orders.values());
    }

    private static List<Item> loadItemsForOrder(FakeDb db, long orderId, SqlLog log) {
        log.log("SELECT * FROM items WHERE order_id = " + orderId);
        return db.itemsByOrder.getOrDefault(orderId, List.of());
    }

    private static Map<Long, List<Item>> loadOrdersWithItems(FakeDb db, SqlLog log) {
        log.log("SELECT o.*, i.* FROM orders o LEFT JOIN items i ON i.order_id = o.id");
        return Map.copyOf(db.itemsByOrder);
    }

    private static void loadItemsBatched(FakeDb db, List<Long> orderIds, SqlLog log) {
        log.log("SELECT * FROM items WHERE order_id IN " + orderIds);
        int total = orderIds.stream().mapToInt(id -> db.itemsByOrder.getOrDefault(id, List.of()).size()).sum();
        System.out.println("Loaded items total=" + total);
    }
}
