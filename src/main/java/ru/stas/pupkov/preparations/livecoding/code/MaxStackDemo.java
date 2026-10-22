package ru.stas.pupkov.preparations.livecoding.code;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Стек с peekMax за O(1): основной стек значений + стек текущих максимумов.
 *
 * <p>На каждом push в maxes кладётся max(value, текущийMax). Тогда peekMax/pop/push — O(1).
 */
public class MaxStackDemo {

    public static void main(String[] args) {
        MaxStack<Integer> stack = new MaxStack<>();
        stack.push(3);
        stack.push(1);
        stack.push(5);
        stack.push(4);

        require(stack.peekMax() == 5, "max after pushes");
        require(stack.pop() == 4, "pop 4");
        require(stack.peekMax() == 5, "max still 5");
        require(stack.pop() == 5, "pop 5");
        require(stack.peekMax() == 3, "max becomes 3");

        // Дубликаты максимума
        MaxStack<Integer> dups = new MaxStack<>();
        dups.push(2);
        dups.push(7);
        dups.push(7);
        require(dups.peekMax() == 7, "dup max");
        require(dups.pop() == 7, "pop first 7");
        require(dups.peekMax() == 7, "max still 7");
        require(dups.pop() == 7, "pop second 7");
        require(dups.peekMax() == 2, "max becomes 2");

        System.out.println("MaxStackDemo: OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Стек значений с O(1) доступом к текущему максимуму.
     *
     * @param <T> тип элементов, сравнимых через {@link Comparable}
     */
    public static final class MaxStack<T extends Comparable<? super T>> {

        private final Deque<T> values = new ArrayDeque<>();
        private final Deque<T> maxes = new ArrayDeque<>();

        /**
         * Кладёт элемент на вершину. O(1).
         *
         * @param value новый элемент (не null)
         */
        public void push(T value) {
            Objects.requireNonNull(value, "value");
            values.push(value);
            if (maxes.isEmpty() || value.compareTo(maxes.peek()) >= 0) {
                maxes.push(value);
            } else {
                maxes.push(maxes.peek());
            }
        }

        /**
         * Снимает и возвращает верхний элемент. O(1).
         *
         * @return снятый элемент
         */
        public T pop() {
            ensureNotEmpty();
            maxes.pop();
            return values.pop();
        }

        /**
         * Возвращает максимальный элемент без удаления. O(1).
         *
         * @return текущий максимум
         */
        public T peekMax() {
            ensureNotEmpty();
            return maxes.peek();
        }

        /**
         * @return true, если стек пуст
         */
        public boolean isEmpty() {
            return values.isEmpty();
        }

        private void ensureNotEmpty() {
            if (values.isEmpty()) {
                throw new NoSuchElementException("stack is empty");
            }
        }
    }
}
