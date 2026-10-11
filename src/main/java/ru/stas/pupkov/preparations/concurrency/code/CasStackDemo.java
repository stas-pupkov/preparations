package ru.stas.pupkov.preparations.concurrency.code;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Минимальный lock-free стек на CAS ({@link AtomicReference#compareAndSet}).
 */
public class CasStackDemo {

    static final class CasStack<T> {
        private final AtomicReference<Node<T>> head = new AtomicReference<>();

        void push(T value) {
            Node<T> created = new Node<>(value, null);
            Node<T> prev;
            do {
                prev = head.get();
                created.next = prev;
            } while (!head.compareAndSet(prev, created));
        }

        T pop() {
            Node<T> current;
            Node<T> next;
            do {
                current = head.get();
                if (current == null) {
                    return null;
                }
                next = current.next;
            } while (!head.compareAndSet(current, next));
            return current.value;
        }
    }

    private static final class Node<T> {
        final T value;
        Node<T> next;

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CasStack<Integer> stack = new CasStack<>();
        Thread[] pushers = new Thread[4];
        for (int i = 0; i < pushers.length; i++) {
            final int base = i * 100;
            pushers[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    stack.push(base + j);
                }
            });
        }
        for (Thread t : pushers) {
            t.start();
        }
        for (Thread t : pushers) {
            t.join();
        }

        int popped = 0;
        while (stack.pop() != null) {
            popped++;
        }
        System.out.println("pushed=200, popped=" + popped);
    }
}
