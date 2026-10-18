package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Почему LIMIT после JOIN коллекции ≠ «N родителей на странице».
 */
public class PaginationJoinFetchDemo {

    record OrderRow(int orderId, String item) {
    }

    public static void main(String[] args) {
        // orders: 1→3 items, 2→1 item, 3→2 items
        List<OrderRow> joinResult = List.of(
                new OrderRow(1, "A"),
                new OrderRow(1, "B"),
                new OrderRow(1, "C"),
                new OrderRow(2, "D"),
                new OrderRow(3, "E"),
                new OrderRow(3, "F")
        );

        System.out.println("=== результат JOIN order ⋈ items (6 строк) ===");
        joinResult.forEach(r -> System.out.println("order=" + r.orderId() + " item=" + r.item()));

        System.out.println();
        System.out.println("=== LIMIT 2 OFFSET 0 по строкам JOIN (как часто делает БД) ===");
        List<OrderRow> limited = joinResult.subList(0, 2);
        limited.forEach(r -> System.out.println("order=" + r.orderId() + " item=" + r.item()));
        System.out.println("уникальных order на «странице»: " + distinctOrders(limited));
        System.out.println("ожидали 2 заказа → получили " + distinctOrders(limited).size()
                + ", коллекция order=1 обрезана");

        System.out.println();
        System.out.println("=== правильный паттерн: страница id, потом fetch ===");
        List<Integer> pageIds = List.of(1, 2); // SELECT id … LIMIT 2 без join коллекции
        System.out.println("page ids=" + pageIds);
        Map<Integer, List<String>> graph = fetchByIds(joinResult, pageIds);
        graph.forEach((id, items) -> System.out.println("order=" + id + " items=" + items));
        System.out.println("родителей ровно " + graph.size() + ", коллекции полные");
    }

    private static List<Integer> distinctOrders(List<OrderRow> rows) {
        return rows.stream().map(OrderRow::orderId).distinct().toList();
    }

    private static Map<Integer, List<String>> fetchByIds(List<OrderRow> all, List<Integer> ids) {
        Map<Integer, List<String>> map = new LinkedHashMap<>();
        for (Integer id : ids) {
            map.put(id, new ArrayList<>());
        }
        for (OrderRow row : all) {
            if (map.containsKey(row.orderId())) {
                map.get(row.orderId()).add(row.item());
            }
        }
        return map;
    }
}
