package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * Prototype: копирование через общий контракт {@code clone}, без знания конкретного класса.
 *
 * Spring-якорь: {@code @Scope("prototype")} даёт новый экземпляр бина при каждом запросе —
 * это не GoF-клонирование состояния, а «новый объект из шаблона определения бина».
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class PrototypeDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Prototype (copy ctor / clone) ===");
        List<Shape> shapes = new ArrayList<>();
        Circle c = new Circle(10, 10, "red", 20);
        shapes.add(c);
        shapes.add(c.copy());
        shapes.add(new Rectangle(0, 0, "blue", 5, 8));

        List<Shape> copies = new ArrayList<>();
        for (Shape s : shapes) {
            copies.add(s.copy());
        }
        System.out.println("originals: " + shapes);
        System.out.println("copies:    " + copies);
        System.out.println("same refs? " + (shapes.get(0) == copies.get(0)));

        System.out.println();
        System.out.println("=== Spring prototype scope (new instance per get) ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            ObjectProvider<PrototypeToken> tokens = ctx.getBeanProvider(PrototypeToken.class);
            PrototypeToken a = tokens.getObject();
            PrototypeToken b = tokens.getObject();
            System.out.println("a=" + a + ", b=" + b + ", same=" + (a == b));
        }
    }

    abstract static class Shape {
        final int x;
        final int y;
        final String color;

        Shape(int x, int y, String color) {
            this.x = x;
            this.y = y;
            this.color = color;
        }

        Shape(Shape source) {
            this(source.x, source.y, source.color);
        }

        abstract Shape copy();
    }

    static final class Circle extends Shape {
        final int radius;

        Circle(int x, int y, String color, int radius) {
            super(x, y, color);
            this.radius = radius;
        }

        Circle(Circle source) {
            super(source);
            this.radius = source.radius;
        }

        @Override
        Circle copy() {
            return new Circle(this);
        }

        @Override
        public String toString() {
            return "Circle(r=" + radius + ",@" + x + "," + y + "," + color + ")";
        }
    }

    static final class Rectangle extends Shape {
        final int w;
        final int h;

        Rectangle(int x, int y, String color, int w, int h) {
            super(x, y, color);
            this.w = w;
            this.h = h;
        }

        Rectangle(Rectangle source) {
            super(source);
            this.w = source.w;
            this.h = source.h;
        }

        @Override
        Rectangle copy() {
            return new Rectangle(this);
        }

        @Override
        public String toString() {
            return "Rect(" + w + "x" + h + ",@" + x + "," + y + "," + color + ")";
        }
    }

    record PrototypeToken(int id) {
    }

    @Configuration
    static class Config {
        private int seq;

        @Bean
        @Scope("prototype")
        PrototypeToken token() {
            return new PrototypeToken(++seq);
        }
    }
}
