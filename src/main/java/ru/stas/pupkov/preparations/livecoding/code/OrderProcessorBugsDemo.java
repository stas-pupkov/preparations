package ru.stas.pupkov.preparations.livecoding.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Воспроизведение багов {@code OrderProcessor} и короткие фиксы.
 */
public class OrderProcessorBugsDemo {

    public static void main(String[] args) {
        demoCalculateTotal();
        demoProcessOrdersCme();
        demoFindOrderEquals();
    }

    private static void demoCalculateTotal() {
        List<Double> prices = List.of(10.0, 20.0);
        try {
            buggyTotal(prices, 10);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("calculateTotal: " + e.getClass().getSimpleName() + " (i <= size)");
        }
        System.out.println("fixed total with 10% discount: " + fixedTotal(prices, 10));
    }

    private static void demoProcessOrdersCme() {
        List<String> orders = new ArrayList<>(List.of("TEST-1", "ORD-2", "TEST-3"));
        try {
            for (String order : orders) {
                if (order.startsWith("TEST")) {
                    orders.remove(order);
                }
            }
        } catch (Exception e) {
            System.out.println("processOrders: " + e.getClass().getSimpleName());
        }
        List<String> copy = new ArrayList<>(List.of("TEST-1", "ORD-2", "TEST-3"));
        copy.removeIf(o -> o.startsWith("TEST"));
        System.out.println("fixed processOrders → " + copy);
    }

    private static void demoFindOrderEquals() {
        List<String> orders = List.of(new String("A-1"), "B-2");
        String id = new String("A-1");
        System.out.println("find with == : " + buggyFind(orders, id));
        System.out.println("find with equals: " + fixedFind(orders, id));
    }

    private static double buggyTotal(List<Double> prices, double discountPercent) {
        double total = 0;
        for (int i = 0; i <= prices.size(); i++) {
            total += prices.get(i);
        }
        return total + (total * discountPercent / 100);
    }

    private static double fixedTotal(List<Double> prices, double discountPercent) {
        double total = 0;
        for (double p : prices) {
            total += p;
        }
        return total * (1 - discountPercent / 100);
    }

    private static String buggyFind(List<String> orders, String id) {
        for (String order : orders) {
            if (order == id) {
                return order;
            }
        }
        return null;
    }

    private static String fixedFind(List<String> orders, String id) {
        for (String order : orders) {
            if (Objects.equals(order, id)) {
                return order;
            }
        }
        return null;
    }
}
