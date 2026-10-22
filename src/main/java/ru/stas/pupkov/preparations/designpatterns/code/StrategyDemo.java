package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Strategy: семейство взаимозаменяемых алгоритмов; выбор в runtime.
 *
 * Spring-якорь: несколько реализаций одного интерфейса + DI / Map injection / {@code @Qualifier}.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class StrategyDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Strategy ===");
        Navigator nav = new Navigator(new RoadStrategy());
        System.out.println(nav.buildRoute("A", "B"));
        nav.setStrategy(new WalkingStrategy());
        System.out.println(nav.buildRoute("A", "B"));

        System.out.println();
        System.out.println("=== Spring: strategies as beans ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService payments = ctx.getBean(PaymentService.class);
            System.out.println(payments.pay("card", 100));
            System.out.println(payments.pay("sbp", 50));
            System.out.println("default: " + ctx.getBean(Checkout.class).charge(10));
        }
    }

    interface RouteStrategy {
        String build(String from, String to);
    }

    static final class RoadStrategy implements RouteStrategy {
        @Override
        public String build(String from, String to) {
            return "road " + from + "->" + to;
        }
    }

    static final class WalkingStrategy implements RouteStrategy {
        @Override
        public String build(String from, String to) {
            return "walk " + from + "->" + to;
        }
    }

    static final class Navigator {
        private RouteStrategy strategy;

        Navigator(RouteStrategy strategy) {
            this.strategy = strategy;
        }

        void setStrategy(RouteStrategy strategy) {
            this.strategy = strategy;
        }

        String buildRoute(String from, String to) {
            return strategy.build(from, to);
        }
    }

    interface PaymentStrategy {
        String pay(int amount);
    }

    static final class CardPayment implements PaymentStrategy {
        @Override
        public String pay(int amount) {
            return "card charged " + amount;
        }
    }

    static final class SbpPayment implements PaymentStrategy {
        @Override
        public String pay(int amount) {
            return "sbp charged " + amount;
        }
    }

    static final class PaymentService {
        private final Map<String, PaymentStrategy> strategies;

        PaymentService(Map<String, PaymentStrategy> strategies) {
            this.strategies = strategies;
        }

        String pay(String channel, int amount) {
            PaymentStrategy strategy = strategies.get(channel);
            if (strategy == null) {
                throw new IllegalArgumentException("unknown channel: " + channel);
            }
            return strategy.pay(amount);
        }
    }

    static final class Checkout {
        private final PaymentStrategy defaultStrategy;

        Checkout(PaymentStrategy defaultStrategy) {
            this.defaultStrategy = defaultStrategy;
        }

        String charge(int amount) {
            return defaultStrategy.pay(amount);
        }
    }

    @Configuration
    static class Config {
        @Bean
        PaymentStrategy card() {
            return new CardPayment();
        }

        @Bean
        PaymentStrategy sbp() {
            return new SbpPayment();
        }

        @Bean
        PaymentService paymentService(Map<String, PaymentStrategy> strategies) {
            // bean names: card, sbp
            return new PaymentService(strategies);
        }

        @Bean
        Checkout checkout(@Qualifier("card") PaymentStrategy card) {
            return new Checkout(card);
        }
    }
}
