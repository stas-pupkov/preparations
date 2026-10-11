package ru.stas.pupkov.preparations.collections.code;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fail-fast итератор ArrayList vs безопасное удаление vs snapshot у CopyOnWriteArrayList.
 */
public class FailFastDemo {

    public static void main(String[] args) {
        demonstrateConcurrentModificationException();
        demonstrateSafeIteratorRemove();
        demonstrateCopyOnWriteSnapshot();
    }

    private static void demonstrateConcurrentModificationException() {
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4));
        System.out.println("--- for-each + list.remove → CME ---");
        try {
            for (Integer value : list) {
                if (value == 2) {
                    list.remove(value);
                }
            }
            System.out.println("ERROR: expected ConcurrentModificationException");
        } catch (java.util.ConcurrentModificationException expected) {
            System.out.println("Caught: " + expected.getClass().getSimpleName());
            System.out.println("List after failed attempt: " + list);
        }
    }

    private static void demonstrateSafeIteratorRemove() {
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4));
        System.out.println("--- iterator.remove (safe for fail-fast) ---");
        Iterator<Integer> iterator = list.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() % 2 == 0) {
                iterator.remove();
            }
        }
        System.out.println("After remove even: " + list);
    }

    private static void demonstrateCopyOnWriteSnapshot() {
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>(List.of(1, 2, 3));
        System.out.println("--- CopyOnWriteArrayList iterator is a snapshot ---");
        Iterator<Integer> snapshot = list.iterator();
        list.add(4);
        System.out.println("List now: " + list);
        System.out.print("Iterator still sees: ");
        snapshot.forEachRemaining(value -> System.out.print(value + " "));
        System.out.println();
    }
}
