package ru.stas.pupkov.preparations.livecoding.code;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Top-3 users by order sum over last 30 days (H2).
 */
public class TopUsersByOrdersDemo {

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:topusers;MODE=PostgreSQL")) {
            try (Statement st = c.createStatement()) {
                st.execute("create table users(id int primary key, name varchar)");
                st.execute("""
                        create table orders(
                          id int primary key,
                          user_id int,
                          amount decimal(12,2),
                          created_at timestamp
                        )
                        """);
                st.execute("insert into users values (1,'Alice'),(2,'Bob'),(3,'Carol'),(4,'Dave')");
                st.execute("""
                        insert into orders values
                        (1,1,100, CURRENT_TIMESTAMP - INTERVAL '5' DAY),
                        (2,1,50,  CURRENT_TIMESTAMP - INTERVAL '10' DAY),
                        (3,2,200, CURRENT_TIMESTAMP - INTERVAL '1' DAY),
                        (4,3,30,  CURRENT_TIMESTAMP - INTERVAL '2' DAY),
                        (5,3,40,  CURRENT_TIMESTAMP - INTERVAL '3' DAY),
                        (6,4,999, CURRENT_TIMESTAMP - INTERVAL '40' DAY)
                        """);

                String sql = """
                        SELECT u.name, SUM(o.amount) AS total_amount
                        FROM users u
                        JOIN orders o ON o.user_id = u.id
                        WHERE o.created_at >= CURRENT_TIMESTAMP - INTERVAL '30' DAY
                        GROUP BY u.id, u.name
                        ORDER BY total_amount DESC
                        LIMIT 3
                        """;
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        System.out.println(rs.getString(1) + " → " + rs.getBigDecimal(2));
                    }
                }
            }
        }
    }
}
