package ru.stas.pupkov.preparations.livecoding.code;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Первый элемент с частотой 1. Для {@code {9,4,9,6,7,4,5}} → 6.
 */
public class FirstNonRepeatingDemo {

    public static void main(String[] args) {
        int[] arr = {9, 4, 9, 6, 7, 4, 5};
        System.out.println("LinkedHashMap → " + firstNonRepeatingLinked(arr));
        System.out.println("HashMap + 2 passes → " + firstNonRepeatingTwoPass(arr));
    }

    /** Порядок первого появления через LinkedHashMap. */
    static int firstNonRepeatingLinked(int[] arr) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int n : arr) {
            counts.merge(n, 1, Integer::sum);
        }
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() == 1) {
                return e.getKey();
            }
        }
        return -1;
    }

    /** Классика whiteboard: частоты, затем проход по исходному массиву. */
    static int firstNonRepeatingTwoPass(int[] arr) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int n : arr) {
            counts.merge(n, 1, Integer::sum);
        }
        for (int n : arr) {
            if (counts.get(n) == 1) {
                return n;
            }
        }
        return -1;
    }
}
