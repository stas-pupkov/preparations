package ru.stas.pupkov.preparations.designpatterns.code;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Memento: сохранить/восстановить состояние, не раскрывая internals наружу.
 *
 * Spring-якорь: прямого GoF-Memento в ядре нет; близкие сценарии — snapshot
 * entity перед dirty-check / ручной undo в UI-слое. Демо — классический редактор.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class MementoDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Memento (editor undo) ===");
        Editor editor = new Editor();
        History history = new History();

        editor.setText("version-1");
        history.push(editor.save());
        editor.setText("version-2");
        history.push(editor.save());
        editor.setText("version-3");
        System.out.println("current: " + editor.getText());

        editor.restore(history.pop());
        System.out.println("undo -> " + editor.getText());
        editor.restore(history.pop());
        System.out.println("undo -> " + editor.getText());
    }

    /** Originator. */
    static final class Editor {
        private String text = "";

        void setText(String text) {
            this.text = text;
        }

        String getText() {
            return text;
        }

        Snapshot save() {
            return new Snapshot(text);
        }

        void restore(Snapshot snapshot) {
            this.text = snapshot.text();
        }
    }

    /** Memento: opaque для caretaker (package-private field access via record component). */
    record Snapshot(String text) {
    }

    /** Caretaker. */
    static final class History {
        private final Deque<Snapshot> stack = new ArrayDeque<>();

        void push(Snapshot snapshot) {
            stack.push(snapshot);
        }

        Snapshot pop() {
            return stack.pop();
        }
    }
}
