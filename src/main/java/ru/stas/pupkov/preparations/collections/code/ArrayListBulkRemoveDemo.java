package ru.stas.pupkov.preparations.collections.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Удаление диапазона из середины ArrayList: N×{@ vs один subList.clear().
 */
public class ArrayListBulkRemoveDemo {

    private static final int SIZE = 200_000;
    private static final int FROM = 80_000;
    private static final int COUNT = 20_000;

    public static void main(String[] args) {
        List<Integer> forLoopRemove = fill();
        List<Integer> forSubList = new ArrayList<>(forLoopRemove);

        long slow = timed(() -> {
            // Ошибочный наивный вариант был бы remove(i) с i++ — ещё и пропускает элементы.
            // Корректный, но медленный: всегда remove(FROM) COUNT раз.
            for (int i = 0; i < COUNT; i++) {
                forLoopRemove.remove(FROM);
            }
        });

        long fast = timed(() -> forSubList.subList(FROM, FROM + COUNT).clear());

        System.out.println("size after slow remove: " + forLoopRemove.size());
        System.out.println("size after subList.clear: " + forSubList.size());
        System.out.println("same result: " + forLoopRemove.equals(forSubList));
        System.out.println("N× remove(FROM): " + slow + " ms");
        System.out.println("subList.clear(): " + fast + " ms");
    }

    private static List<Integer> fill() {
        List<Integer> list = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            list.add(i);
        }
        return list;
    }

    private static long timed(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
