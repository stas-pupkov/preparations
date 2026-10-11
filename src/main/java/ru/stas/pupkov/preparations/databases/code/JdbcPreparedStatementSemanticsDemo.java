package ru.stas.pupkov.preparations.databases.code;

import java.util.List;

/**
 * Семантика {@code Statement} vs {@code PreparedStatement}: склейка SQL vs bind параметров.
 * Без реального JDBC — моделирует, почему конкатенация опасна и почему bind обязателен.
 */
public class JdbcPreparedStatementSemanticsDemo {

    public static void main(String[] args) {
        String login = "alice";
        String injection = "alice' OR '1'='1";

        System.out.println("=== Statement-style concatenation (unsafe) ===");
        System.out.println(concatSql(login));
        System.out.println(concatSql(injection));
        System.out.println("Injected predicate always true -> auth bypass risk");

        System.out.println();
        System.out.println("=== PreparedStatement-style bind (safe) ===");
        System.out.println(boundSql(login));
        System.out.println(boundSql(injection));
        System.out.println("Whole string is ONE parameter value; OR is not SQL syntax");

        System.out.println();
        System.out.println("=== Batch mental model ===");
        List<OrderLine> lines = List.of(
                new OrderLine(1, 2),
                new OrderLine(1, 5),
                new OrderLine(2, 1)
        );
        System.out.println("Same prepared SQL, different binds:");
        for (OrderLine line : lines) {
            System.out.println("  " + boundInsert(line));
        }
        System.out.println("JDBC: ps.addBatch() / executeBatch(); IDENTITY often disables multi-row batch");
    }

    private static String concatSql(String login) {
        return "SELECT * FROM users WHERE login = '" + login + "' AND active = true";
    }

    private static String boundSql(String login) {
        return "SELECT * FROM users WHERE login = ? AND active = true  | bind[1]=" + quote(login);
    }

    private static String boundInsert(OrderLine line) {
        return "INSERT INTO order_line(order_id, qty) VALUES (?, ?)  | bind="
                + List.of(line.orderId, line.qty);
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private record OrderLine(int orderId, int qty) {
    }
}
