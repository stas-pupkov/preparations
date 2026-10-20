package ru.stas.pupkov.preparations.spring.code;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Типичные сценарии {@code @Bean}: сторонний класс, зависимости через параметры,
 * несколько бинов одного типа, init/destroy, вызов {@code @Bean} из {@code @Bean}.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class BeanUsageDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);

        System.out.println("=== 1. Сторонний / POJO через @Bean ===");
        Clock clock = ctx.getBean(Clock.class);
        System.out.println("  now=" + clock.now());

        System.out.println();
        System.out.println("=== 2. Зависимости как параметры @Bean-метода ===");
        ReportService reports = ctx.getBean(ReportService.class);
        System.out.println("  " + reports.render());

        System.out.println();
        System.out.println("=== 3. Несколько бинов одного типа: @Primary + @Qualifier ===");
        Greeter defaultGreeter = ctx.getBean(Greeter.class);
        Greeter formal = ctx.getBean("formalGreeter", Greeter.class);
        System.out.println("  default (@Primary): " + defaultGreeter.greet("Stas"));
        System.out.println("  formal (@Qualifier): " + formal.greet("Stas"));

        System.out.println();
        System.out.println("=== 4. initMethod / destroyMethod ===");
        ConnectionPool pool = ctx.getBean(ConnectionPool.class);
        System.out.println("  pool ready=" + pool.isReady());

        System.out.println();
        System.out.println("=== close → destroyMethod ===");
        ctx.close();
    }

    /** Имитация класса из библиотеки: не наш код, на него нельзя повесить @Component. */
    static final class Clock {
        String now() {
            return "t0";
        }
    }

    static final class Formatter {
        String format(String raw) {
            return "[" + raw + "]";
        }
    }

    static final class ReportService {
        private final Clock clock;
        private final Formatter formatter;

        ReportService(Clock clock, Formatter formatter) {
            this.clock = clock;
            this.formatter = formatter;
        }

        String render() {
            return formatter.format("report@" + clock.now());
        }
    }

    interface Greeter {
        String greet(String name);
    }

    static final class CasualGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "hi, " + name;
        }
    }

    static final class FormalGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Good day, " + name;
        }
    }

    /** Ресурс с явным жизненным циклом — удобно задать init/destroy на @Bean. */
    static final class ConnectionPool {
        private boolean ready;

        void start() {
            ready = true;
            System.out.println("  [initMethod] pool.start()");
        }

        void shutdown() {
            ready = false;
            System.out.println("  [destroyMethod] pool.shutdown()");
        }

        boolean isReady() {
            return ready;
        }
    }

    /**
     * Клиент, которому нужен именно formal Greeter — параметр помечен {@code @Qualifier}.
     */
    static final class FormalMailer {
        private final Greeter greeter;

        FormalMailer(@Qualifier("formalGreeter") Greeter greeter) {
            this.greeter = greeter;
        }

        String compose(String name) {
            return greeter.greet(name);
        }
    }

    @Configuration
    static class AppConfig {

        /** 1. Фабрика для класса без stereotype-аннотаций. */
        @Bean
        Clock clock() {
            return new Clock();
        }

        @Bean
        Formatter formatter() {
            return new Formatter();
        }

        /**
         * 2. Зависимости контейнер подставляет в параметры метода
         * (эквивалент constructor injection для фабрики).
         */
        @Bean
        ReportService reportService(Clock clock, Formatter formatter) {
            return new ReportService(clock, formatter);
        }

        /** 3a. Несколько реализаций одного типа — имя бина = имя метода. */
        @Bean
        @Primary
        Greeter casualGreeter() {
            return new CasualGreeter();
        }

        @Bean
        Greeter formalGreeter() {
            return new FormalGreeter();
        }

        /**
         * 3b. Явный выбор через {@code @Qualifier} на параметре.
         * В full {@code @Configuration} вызов {@code formalGreeter()} ниже идёт через
         * прокси конфигурации → тот же singleton, что и у контейнера.
         */
        @Bean
        FormalMailer formalMailer(@Qualifier("formalGreeter") Greeter greeter) {
            // альтернатива параметру: return new FormalMailer(formalGreeter());
            return new FormalMailer(greeter);
        }

        /** 4. Явные init / destroy, если нет {@code @PostConstruct} / {@code AutoCloseable}. */
        @Bean(initMethod = "start", destroyMethod = "shutdown")
        ConnectionPool connectionPool() {
            return new ConnectionPool();
        }
    }
}
