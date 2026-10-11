package ru.stas.pupkov.preparations.databases.code;

import java.util.List;
import java.util.Objects;

/**
 * {@code COUNT(*)} считает строки; {@code COUNT(col)} игнорирует NULL в столбце.
 */
public class SqlCountAggregateDemo {

    record Order(Integer discount) {
    }

    public static void main(String[] args) {
        List<Order> orders = List.of(
                new Order(10),
                new Order(null),
                new Order(5),
                new Order(null)
        );

        long countStar = orders.size();
        long countDiscount = orders.stream().map(Order::discount).filter(Objects::nonNull).count();
        long sumDiscount = orders.stream().map(Order::discount).filter(Objects::nonNull).mapToInt(i -> i).sum();

        System.out.println("rows          = " + orders.size());
        System.out.println("COUNT(*)      = " + countStar);
        System.out.println("COUNT(discount) = " + countDiscount + " (NULL skipped)");
        System.out.println("SUM(discount)   = " + sumDiscount + " (NULL skipped)");
    }
}
