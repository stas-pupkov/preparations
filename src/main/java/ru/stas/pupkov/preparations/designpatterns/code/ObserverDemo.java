package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Observer: подписка на события издателя.
 *
 * Spring-якорь: {@code ApplicationEventPublisher} + {@code @EventListener}
 * (внутрипроцессный pub/sub).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class ObserverDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Observer ===");
        Store store = new Store();
        store.subscribe(event -> System.out.println("  email: " + event));
        store.subscribe(event -> System.out.println("  sms: " + event));
        store.newItem("keyboard");

        System.out.println();
        System.out.println("=== Spring @EventListener ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            ctx.getBean(StoreService.class).publishNewItem("mouse");
        }
    }

    @FunctionalInterface
    interface Listener {
        void update(String event);
    }

    static final class Store {
        private final List<Listener> listeners = new ArrayList<>();

        void subscribe(Listener listener) {
            listeners.add(listener);
        }

        void newItem(String item) {
            String event = "new item: " + item;
            for (Listener listener : listeners) {
                listener.update(event);
            }
        }
    }

    record ItemArrivedEvent(String item) {
    }

    static final class StoreService {
        private final ApplicationEventPublisher publisher;

        StoreService(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        void publishNewItem(String item) {
            publisher.publishEvent(new ItemArrivedEvent(item));
        }
    }

    static final class EmailNotifier {
        @EventListener
        void on(ItemArrivedEvent event) {
            System.out.println("  [spring-email] " + event.item());
        }
    }

    static final class SmsNotifier {
        @EventListener
        void on(ItemArrivedEvent event) {
            System.out.println("  [spring-sms] " + event.item());
        }
    }

    @Configuration
    static class Config {
        @Bean
        StoreService storeService(ApplicationEventPublisher publisher) {
            return new StoreService(publisher);
        }

        @Bean
        EmailNotifier emailNotifier() {
            return new EmailNotifier();
        }

        @Bean
        SmsNotifier smsNotifier() {
            return new SmsNotifier();
        }
    }
}
