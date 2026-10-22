package ru.stas.pupkov.preparations.livecoding.code;

import java.io.IOException;

/**
 * Primary из try, исключение из {@code close} — в suppressed.
 * Консоль: закрытие → сообщение primary → «Перехватили…».
 */
public class TryWithResourcesSuppressedDemo {

    public static void main(String[] args) {
        exception();
    }

    private static void exception() {
        try (AutoCloseable a = createResources()) {
            throw new IllegalArgumentException("Исключение в try");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("Перехватили исключение");
            System.out.println("suppressed count=" + e.getSuppressed().length);
        }
    }

    private static AutoCloseable createResources() {
        return () -> {
            System.out.println("Начинаем закрывать ресурс");
            throw new IOException("Exception");
        };
    }
}
