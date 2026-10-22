package ru.stas.pupkov.preparations.livecoding.code;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Семантика NULL в IN / NOT IN / {@code != null} (H2, PostgreSQL mode).
 * Данные: (1,'Dima'), (2,''), (3,'Ivan'), (4,NULL).
 */
public class SqlNullInNotInDemo {

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:sqlnull;MODE=PostgreSQL")) {
            try (Statement st = c.createStatement()) {
                st.execute("create table test(id int, name varchar)");
                st.execute("insert into test values (1,'Dima'),(2,''),(3,'Ivan'),(4,null)");
            }

            print(c, "1 IN (... null)",
                    "select id from test where name in ('Dima', '', null)");
            print(c, "2 OR IS NULL",
                    "select id from test where name = 'Dima' or name = '' or name is null");
            print(c, "3 NOT IN (... null)",
                    "select id from test where name not in ('Dima', '', null)");
            print(c, "4 != null (wrong)",
                    "select id from test where name != 'Dima' and name != '' and name != null");
        }
    }

    private static void print(Connection c, String title, String sql) throws Exception {
        List<Integer> ids = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        System.out.println(title + " → " + (ids.isEmpty() ? "(empty)" : ids));
    }
}
