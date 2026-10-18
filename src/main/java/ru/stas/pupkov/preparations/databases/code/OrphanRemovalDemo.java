package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.List;

/**
 * orphanRemoval: отвязка ребёнка от агрегата → DELETE.
 * CascadeType.REMOVE: удаление родителя → DELETE детей.
 */
public class OrphanRemovalDemo {

    static final class Order {
        final Long id;
        final List<OrderLine> lines = new ArrayList<>();

        Order(Long id) {
            this.id = id;
        }

        void addLine(OrderLine line) {
            lines.add(line);
            line.order = this;
        }

        void removeLine(OrderLine line) {
            lines.remove(line);
            line.order = null;
        }
    }

    static final class OrderLine {
        final Long id;
        final String sku;
        Order order;

        OrderLine(Long id, String sku) {
            this.id = id;
            this.sku = sku;
        }
    }

    public static void main(String[] args) {
        Order order = new Order(1L);
        OrderLine a = new OrderLine(10L, "SKU-A");
        OrderLine b = new OrderLine(11L, "SKU-B");
        order.addLine(a);
        order.addLine(b);

        System.out.println("=== убрали линию из коллекции (отвязка от агрегата) ===");
        order.removeLine(a);
        System.out.println("in-memory lines=" + order.lines.stream().map(l -> l.sku).toList());
        System.out.println("line A.order=" + a.order);

        System.out.println();
        System.out.println("-- без orphanRemoval (часто только разрыв FK) --");
        System.out.println("SQL: UPDATE order_line SET order_id=NULL WHERE id=10");
        System.out.println("     -- строка order_line остаётся в таблице");

        System.out.println();
        System.out.println("-- с orphanRemoval = true --");
        System.out.println("SQL: DELETE FROM order_line WHERE id=10");
        System.out.println("     -- сирота удалена из БД");

        System.out.println();
        System.out.println("=== CascadeType.REMOVE: em.remove(order) ===");
        System.out.println("SQL: DELETE FROM order_line WHERE order_id=1");
        System.out.println("SQL: DELETE FROM orders WHERE id=1");
        System.out.println("     -- срабатывает при удалении родителя, не при remove из List");

        System.out.println();
        System.out.println("Итог: orphanRemoval ≠ CascadeType.REMOVE.");
        System.out.println("Ставить на агрегат (OrderLine), не на shared (User/Product).");
    }
}
