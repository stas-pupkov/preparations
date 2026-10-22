package ru.stas.pupkov.preparations.livecoding.code;

import java.util.HashMap;
import java.util.Map;

/**
 * Все элементы попарно, один уникален: XOR → O(1) память; HashMap → O(n).
 * Пример: {@code [1,0,3,-2,9,9,1,-2,0]} → 3.
 */
public class SingleNumberXorDemo {

    public static void main(String[] args) {
        int[] arr = {1, 0, 3, -2, 9, 9, 1, -2, 0};
        System.out.println("XOR → " + findByXor(arr));
        System.out.println("HashMap → " + findByMap(arr));
    }

    static int findByXor(int[] arr) {
        int unique = 0;
        for (int num : arr) {
            unique ^= num;
        }
        return unique;
    }

    static int findByMap(int[] arr) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int value : arr) {
            counts.merge(value, 1, Integer::sum);
        }
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() == 1) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("no unique element");
    }
}
