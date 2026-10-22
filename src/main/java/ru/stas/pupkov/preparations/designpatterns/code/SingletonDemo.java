package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Singleton (GoF): один экземпляр класса + глобальная точка доступа.
 *
 * Spring-якорь: default scope {@code singleton} — один экземпляр <b>на контейнер</b>,
 * создаётся и инжектится контейнером. Это не скрытый конструктор и не static getInstance.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class SingletonDemo {

    public static void main(String[] args) {
        System.out.println("=== GoF enum singleton (preferred in Java) ===");
        ConfigRegistry a = ConfigRegistry.INSTANCE;
        ConfigRegistry b = ConfigRegistry.INSTANCE;
        a.put("app.name", "preparations");
        System.out.println("same instance: " + (a == b));
        System.out.println("value via b: " + b.get("app.name"));

        System.out.println();
        System.out.println("=== GoF lazy DCL (historical; prefer enum) ===");
        DatabaseConnection c1 = DatabaseConnection.getInstance();
        DatabaseConnection c2 = DatabaseConnection.getInstance();
        System.out.println("same instance: " + (c1 == c2));
        c1.query("SELECT 1");

        System.out.println();
        System.out.println("=== Spring container singleton ≠ GoF ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(SpringConfig.class)) {
            Service s1 = ctx.getBean(Service.class);
            Service s2 = ctx.getBean(Service.class);
            System.out.println("same bean: " + (s1 == s2));
            System.out.println("constructed via Spring, not getInstance(); id=" + s1.id());
        }
    }

    enum ConfigRegistry {
        INSTANCE;

        private final java.util.Map<String, String> values = new java.util.concurrent.ConcurrentHashMap<>();

        void put(String k, String v) {
            values.put(k, v);
        }

        String get(String k) {
            return values.get(k);
        }
    }

    static final class DatabaseConnection {
        private static volatile DatabaseConnection instance;

        private DatabaseConnection() {
        }

        static DatabaseConnection getInstance() {
            DatabaseConnection local = instance;
            if (local == null) {
                synchronized (DatabaseConnection.class) {
                    local = instance;
                    if (local == null) {
                        instance = local = new DatabaseConnection();
                    }
                }
            }
            return local;
        }

        void query(String sql) {
            System.out.println("  query: " + sql);
        }
    }

    record Service(int id) {
    }

    @Configuration
    static class SpringConfig {
        @Bean
        Service service() {
            return new Service(42);
        }
    }
}
