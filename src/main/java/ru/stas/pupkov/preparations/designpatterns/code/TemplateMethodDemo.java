package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Template Method: скелет алгоритма в базе, варьируемые шаги — в подклассах / колбэках.
 *
 * Spring-якорь: {@link JdbcTemplate} — template method + callback (RowMapper и т.д.):
 * каркас JDBC-цикла фиксирован, различается только «что сделать с ResultSet».
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class TemplateMethodDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Template Method ===");
        new DocDataMiner().mine("report.doc");
        new CsvDataMiner().mine("report.csv");

        System.out.println();
        System.out.println("=== Spring JdbcTemplate (template + callback) ===");
        var ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE item(id INT PRIMARY KEY, title VARCHAR(64))");
        jdbc.update("INSERT INTO item VALUES (1, 'alpha')");
        String title = jdbc.query("SELECT title FROM item WHERE id = ?", rs -> {
            rs.next();
            return rs.getString(1);
        }, 1);
        System.out.println("  title=" + title);
    }

    abstract static class DataMiner {
        /** Шаблонный метод — final по смыслу. */
        final void mine(String path) {
            String raw = openFile(path);
            String data = extractData(raw);
            String analyzed = analyze(data);
            sendReport(analyzed);
        }

        abstract String openFile(String path);

        abstract String extractData(String raw);

        String analyze(String data) {
            return "stats(" + data + ")";
        }

        void sendReport(String report) {
            System.out.println("  report: " + report);
        }
    }

    static final class DocDataMiner extends DataMiner {
        @Override
        String openFile(String path) {
            System.out.println("  open DOC " + path);
            return "DOC-BYTES";
        }

        @Override
        String extractData(String raw) {
            return "doc-payload";
        }
    }

    static final class CsvDataMiner extends DataMiner {
        @Override
        String openFile(String path) {
            System.out.println("  open CSV " + path);
            return "CSV-BYTES";
        }

        @Override
        String extractData(String raw) {
            return "csv-payload";
        }
    }
}
