package ru.stas.pupkov.preparations.databases.code;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EXISTS / semi-join: достаточно факта существования строки, дубликаты не размножают результат.
 */
public class SqlExistsSemiJoinDemo {

    record Customer(int id, String name) {
    }

    record Order(int customerId, String sku) {
    }

    public static void main(String[] args) {
        List<Customer> customers = List.of(
                new Customer(1, "Ann"),
                new Customer(2, "Bob"),
                new Customer(3, "Eve")
        );
        List<Order> orders = List.of(
                new Order(1, "A"),
                new Order(1, "B"),
                new Order(1, "C"),
                new Order(3, "A")
        );

        // INNER JOIN размножит Ann на 3 строки
        long joinRows = customers.stream()
                .flatMap(c -> orders.stream().filter(o -> o.customerId() == c.id()).map(o -> c))
                .count();

        // EXISTS / semi-join: Ann один раз
        Set<Integer> withOrders = orders.stream().map(Order::customerId).collect(Collectors.toSet());
        List<String> existsResult = customers.stream()
                .filter(c -> withOrders.contains(c.id()))
                .map(Customer::name)
                .toList();

        System.out.println("INNER JOIN row count = " + joinRows);
        System.out.println("EXISTS customers     = " + existsResult);
    }
}
