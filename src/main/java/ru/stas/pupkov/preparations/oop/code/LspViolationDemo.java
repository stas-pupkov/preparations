package ru.stas.pupkov.preparations.oop.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Нарушение LSP: подтип усиливает предусловия / ломает ожидания контракта базового типа.
 *
 * <p>Клиентский код, написанный против {@link Rectangle}, перестаёт быть корректным для {@link Square}.
 */
public class LspViolationDemo {

    static class Rectangle {
        protected int width;
        protected int height;

        void setWidth(int width) {
            this.width = width;
        }

        void setHeight(int height) {
            this.height = height;
        }

        int area() {
            return width * height;
        }
    }

    /** Классический антипример: Square «является» Rectangle только геометрически, не по контракту мутаторов. */
    static class Square extends Rectangle {
        @Override
        void setWidth(int width) {
            this.width = width;
            this.height = width;
        }

        @Override
        void setHeight(int height) {
            this.width = height;
            this.height = height;
        }
    }

    static int stretchWidthAndReadArea(Rectangle rectangle) {
        rectangle.setWidth(5);
        rectangle.setHeight(4);
        return rectangle.area(); // контракт клиента: ожидает 20
    }

    public static void main(String[] args) {
        System.out.println("Rectangle area = " + stretchWidthAndReadArea(new Rectangle())); // 20
        System.out.println("Square as Rectangle area = " + stretchWidthAndReadArea(new Square())); // 16 — сюрприз

        List<? extends Number> numbers = List.of(1, 2, 3);
        // numbers.add(1); // не скомпилируется: PECS / variance — отдельный слой «контракта типа»
        System.out.println("numbers size = " + numbers.size());

        List<Number> writable = new ArrayList<>();
        writable.add(1);
        writable.add(2.5);
        System.out.println(writable);
    }
}
