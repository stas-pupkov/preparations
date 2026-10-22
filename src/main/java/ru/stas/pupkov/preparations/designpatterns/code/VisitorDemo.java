package ru.stas.pupkov.preparations.designpatterns.code;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Visitor: новая операция над иерархией без изменения классов элементов (double dispatch).
 *
 * Spring/JDK-якорь: {@link java.nio.file.FileVisitor} / {@link SimpleFileVisitor} —
 * обход дерева файлов с отдельной логикой visit*.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class VisitorDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Classic Visitor (shapes → XML) ===");
        List<Shape> shapes = List.of(
                new Dot(1, 2),
                new Circle(0, 0, 5),
                new Rectangle(1, 1, 4, 3));
        XmlExportVisitor xml = new XmlExportVisitor();
        for (Shape shape : shapes) {
            shape.accept(xml);
        }
        System.out.println(xml.result());

        System.out.println();
        System.out.println("=== JDK FileVisitor ===");
        Path root = Files.createTempDirectory("visitor-demo");
        Files.writeString(root.resolve("a.txt"), "x");
        List<String> visited = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                visited.add(file.getFileName().toString());
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("  visited files: " + visited);
        Files.deleteIfExists(root.resolve("a.txt"));
        Files.deleteIfExists(root);
    }

    interface Shape {
        void accept(Visitor visitor);
    }

    interface Visitor {
        void visitDot(Dot dot);

        void visitCircle(Circle circle);

        void visitRectangle(Rectangle rectangle);
    }

    static final class Dot implements Shape {
        final int x;
        final int y;

        Dot(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitDot(this);
        }
    }

    static final class Circle implements Shape {
        final int x;
        final int y;
        final int r;

        Circle(int x, int y, int r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitCircle(this);
        }
    }

    static final class Rectangle implements Shape {
        final int x;
        final int y;
        final int w;
        final int h;

        Rectangle(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitRectangle(this);
        }
    }

    static final class XmlExportVisitor implements Visitor {
        private final StringBuilder out = new StringBuilder();

        @Override
        public void visitDot(Dot dot) {
            out.append("<dot x=\"").append(dot.x).append("\" y=\"").append(dot.y).append("\"/>\n");
        }

        @Override
        public void visitCircle(Circle circle) {
            out.append("<circle x=\"").append(circle.x).append("\" y=\"").append(circle.y)
                    .append("\" r=\"").append(circle.r).append("\"/>\n");
        }

        @Override
        public void visitRectangle(Rectangle rectangle) {
            out.append("<rect x=\"").append(rectangle.x).append("\" y=\"").append(rectangle.y)
                    .append("\" w=\"").append(rectangle.w).append("\" h=\"").append(rectangle.h)
                    .append("\"/>\n");
        }

        String result() {
            return out.toString();
        }
    }
}
