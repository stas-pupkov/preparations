package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Factory Method: создание продукта делегируется «фабричному» шагу,
 * клиент зависит от интерфейса продукта.
 *
 * Spring-якорь: {@link FactoryBean} — фабричный метод на уровне контейнера
 * ({@code getObject()} / {@code getObjectType()}).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class FactoryMethodDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Factory Method ===");
        Logistics road = new RoadLogistics();
        Logistics sea = new SeaLogistics();
        road.planDelivery("box-1");
        sea.planDelivery("box-2");

        System.out.println();
        System.out.println("=== Spring FactoryBean ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            Transport bean = ctx.getBean(Transport.class);
            bean.deliver("box-3");
            System.out.println("bean class: " + bean.getClass().getSimpleName());
        }
    }

    interface Transport {
        void deliver(String cargo);
    }

    static final class Truck implements Transport {
        @Override
        public void deliver(String cargo) {
            System.out.println("  truck delivers " + cargo);
        }
    }

    static final class Ship implements Transport {
        @Override
        public void deliver(String cargo) {
            System.out.println("  ship delivers " + cargo);
        }
    }

    /** Creator: фабричный метод + бизнес-логика вокруг продукта. */
    abstract static class Logistics {
        abstract Transport createTransport();

        final void planDelivery(String cargo) {
            Transport t = createTransport();
            System.out.println(getClass().getSimpleName() + " plans route");
            t.deliver(cargo);
        }
    }

    static final class RoadLogistics extends Logistics {
        @Override
        Transport createTransport() {
            return new Truck();
        }
    }

    static final class SeaLogistics extends Logistics {
        @Override
        Transport createTransport() {
            return new Ship();
        }
    }

    /** Spring: бин типа Transport создаётся через FactoryBean. */
    static final class ShipFactoryBean implements FactoryBean<Transport> {
        @Override
        public Transport getObject() {
            return new Ship();
        }

        @Override
        public Class<?> getObjectType() {
            return Transport.class;
        }
    }

    @Configuration
    static class Config {
        @Bean
        FactoryBean<Transport> transportFactory() {
            return new ShipFactoryBean();
        }
    }
}
