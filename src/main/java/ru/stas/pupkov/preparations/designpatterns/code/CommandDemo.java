package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Command: запрос как объект — очередь, лог, undo.
 *
 * Spring-якорь: {@link ApplicationEvent} / listener — близкий механизм «действие как объект»,
 * но без классического undo; для команд чаще свой bus / {@code ApplicationRunner} jobs.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class CommandDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Command + undo ===");
        Editor editor = new Editor();
        CommandHistory history = new CommandHistory();

        Command typeHi = new TypeCommand(editor, "Hi");
        typeHi.execute();
        history.push(typeHi);

        Command typeThere = new TypeCommand(editor, " there");
        typeThere.execute();
        history.push(typeThere);
        System.out.println("text=" + editor.text());

        history.undo();
        System.out.println("after undo: " + editor.text());

        System.out.println();
        System.out.println("=== Spring ApplicationEvent as command-like message ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            ApplicationEventPublisher publisher = ctx;
            publisher.publishEvent(new PlaceOrderCommand("order-1"));
        }
    }

    interface Command {
        void execute();

        void undo();
    }

    static final class Editor {
        private final StringBuilder text = new StringBuilder();

        void append(String chunk) {
            text.append(chunk);
        }

        void deleteLast(int n) {
            text.delete(Math.max(0, text.length() - n), text.length());
        }

        String text() {
            return text.toString();
        }
    }

    static final class TypeCommand implements Command {
        private final Editor editor;
        private final String chunk;

        TypeCommand(Editor editor, String chunk) {
            this.editor = editor;
            this.chunk = chunk;
        }

        @Override
        public void execute() {
            editor.append(chunk);
        }

        @Override
        public void undo() {
            editor.deleteLast(chunk.length());
        }
    }

    static final class CommandHistory {
        private final Deque<Command> stack = new ArrayDeque<>();

        void push(Command command) {
            stack.push(command);
        }

        void undo() {
            if (!stack.isEmpty()) {
                stack.pop().undo();
            }
        }
    }

    static final class PlaceOrderCommand extends ApplicationEvent {
        private final String orderId;

        PlaceOrderCommand(String orderId) {
            super(orderId);
            this.orderId = orderId;
        }

        String orderId() {
            return orderId;
        }
    }

    static final class OrderHandler {
        @EventListener
        void on(PlaceOrderCommand cmd) {
            System.out.println("  handle PlaceOrderCommand id=" + cmd.orderId());
        }
    }

    @Configuration
    static class Config {
        @Bean
        OrderHandler orderHandler() {
            return new OrderHandler();
        }
    }
}
