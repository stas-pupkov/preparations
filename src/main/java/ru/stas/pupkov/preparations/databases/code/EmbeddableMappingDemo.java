package ru.stas.pupkov.preparations.databases.code;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Модель {@code @Embeddable}: поля адреса лежат в той же «таблице», что и заказ.
 * Отдельной entity/таблицы у Address нет.
 */
public class EmbeddableMappingDemo {

    static final class Address {
        final String city;
        final String street;

        Address(String city, String street) {
            this.city = city;
            this.street = street;
        }
    }

    static final class Order {
        final long id;
        final Address delivery;

        Order(long id, Address delivery) {
            this.id = id;
            this.delivery = delivery;
        }
    }

    /** Плоская строка таблицы orders. */
    static Map<String, Object> toRow(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.id);
        row.put("city", order.delivery.city);
        row.put("street", order.delivery.street);
        return row;
    }

    public static void main(String[] args) {
        Order order = new Order(42, new Address("Moscow", "Tverskaya 1"));
        Map<String, Object> row = toRow(order);

        System.out.println("Java: Order + embedded Address");
        System.out.println("  order.id=" + order.id);
        System.out.println("  order.delivery=" + order.delivery.city + ", " + order.delivery.street);
        System.out.println("Table orders (one row, no address table):");
        row.forEach((col, val) -> System.out.println("  " + col + " = " + val));
        System.out.println("Нет SELECT из address — колонки принадлежат orders.");
    }
}
