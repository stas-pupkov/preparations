package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Facade: упрощённый вход в сложную подсистему.
 *
 * Spring-якорь: {@link JdbcTemplate} — фасад над JDBC (Connection/Statement/ResultSet/exceptions).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class FacadeDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Facade (video converter) ===");
        VideoConverter facade = new VideoConverter();
        File result = facade.convert("cats.ogg", "mp4");
        System.out.println("  result: " + result.name());

        System.out.println();
        System.out.println("=== Spring JdbcTemplate as Facade ===");
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE demo(id INT PRIMARY KEY, name VARCHAR(64))");
        jdbc.update("INSERT INTO demo(id, name) VALUES (?, ?)", 1, "facade");
        String name = jdbc.queryForObject("SELECT name FROM demo WHERE id = ?", String.class, 1);
        System.out.println("  row name=" + name);
    }

    record File(String name) {
    }

    // --- «сложная» подсистема ---

    static final class VideoFile {
        final String name;

        VideoFile(String name) {
            this.name = name;
        }
    }

    static final class CodecFactory {
        static String extract(VideoFile file) {
            return file.name.endsWith(".ogg") ? "ogg" : "mp4";
        }
    }

    static final class BitrateReader {
        static String read(String filename, String codec) {
            return "buffer(" + filename + "," + codec + ")";
        }

        static String convert(String buffer, String destCodec) {
            return buffer + "->" + destCodec;
        }
    }

    static final class AudioMixer {
        String fix(String data) {
            return data + "+audio";
        }
    }

    /** Фасад: один метод вместо оркестрации дюжины классов. */
    static final class VideoConverter {
        File convert(String filename, String format) {
            VideoFile file = new VideoFile(filename);
            String source = CodecFactory.extract(file);
            String dest = format.equals("mp4") ? "mpeg4" : "ogg";
            String buffer = BitrateReader.read(filename, source);
            String converted = BitrateReader.convert(buffer, dest);
            String mixed = new AudioMixer().fix(converted);
            return new File(mixed + ".out");
        }
    }
}
