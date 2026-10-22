package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

/**
 * Abstract Factory: семейство согласованных продуктов без привязки клиента к классам.
 *
 * Spring-якорь: выбор конкретной фабрики через {@code @Profile} / конфиг окружения
 * (win/mac UI, test/prod DataSource-семейства и т.п.).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class AbstractFactoryDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic: inject factory ===");
        render(new Application(new WinFactory()));
        render(new Application(new MacFactory()));

        System.out.println();
        System.out.println("=== Spring: profile selects factory family ===");
        runWithProfile("win");
        runWithProfile("mac");
    }

    private static void render(Application app) {
        app.createUi();
        app.paint();
    }

    private static void runWithProfile(String profile) {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.setActiveProfiles(profile);
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.setEnvironment(env);
            ctx.register(SpringConfig.class);
            ctx.refresh();
            Application app = ctx.getBean(Application.class);
            System.out.println("profile=" + profile);
            app.createUi();
            app.paint();
        }
    }

    interface Button {
        void paint();
    }

    interface Checkbox {
        void paint();
    }

    interface GuiFactory {
        Button createButton();

        Checkbox createCheckbox();
    }

    static final class WinButton implements Button {
        @Override
        public void paint() {
            System.out.println("  WinButton");
        }
    }

    static final class WinCheckbox implements Checkbox {
        @Override
        public void paint() {
            System.out.println("  WinCheckbox");
        }
    }

    static final class MacButton implements Button {
        @Override
        public void paint() {
            System.out.println("  MacButton");
        }
    }

    static final class MacCheckbox implements Checkbox {
        @Override
        public void paint() {
            System.out.println("  MacCheckbox");
        }
    }

    static final class WinFactory implements GuiFactory {
        @Override
        public Button createButton() {
            return new WinButton();
        }

        @Override
        public Checkbox createCheckbox() {
            return new WinCheckbox();
        }
    }

    static final class MacFactory implements GuiFactory {
        @Override
        public Button createButton() {
            return new MacButton();
        }

        @Override
        public Checkbox createCheckbox() {
            return new MacCheckbox();
        }
    }

    static final class Application {
        private final GuiFactory factory;
        private Button button;
        private Checkbox checkbox;

        Application(GuiFactory factory) {
            this.factory = factory;
        }

        void createUi() {
            button = factory.createButton();
            checkbox = factory.createCheckbox();
        }

        void paint() {
            button.paint();
            checkbox.paint();
        }
    }

    @Configuration
    static class SpringConfig {

        @Bean
        @Profile("win")
        GuiFactory winFactory() {
            return new WinFactory();
        }

        @Bean
        @Profile("mac")
        GuiFactory macFactory() {
            return new MacFactory();
        }

        @Bean
        Application application(GuiFactory factory) {
            return new Application(factory);
        }
    }
}
