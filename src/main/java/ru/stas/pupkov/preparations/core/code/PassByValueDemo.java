package ru.stas.pupkov.preparations.core.code;

import java.util.Arrays;

/**
 * В Java всё передаётся by value: копируется значение переменной (примитив или ссылка).
 * Переприсвоение параметра невидимо снаружи; мутация объекта по той же ссылке — видима.
 */
public class PassByValueDemo {

    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "Box(" + value + ")";
        }
    }

    static void reassignPrimitive(int n) {
        n = 99;
    }

    static void reassignReference(Box box) {
        box = new Box(99);
    }

    static void mutateThroughReference(Box box) {
        box.value = 99;
    }

    static void reassignArray(int[] arr) {
        arr = new int[] {9, 9, 9};
    }

    static void mutateArray(int[] arr) {
        arr[0] = 99;
    }

    public static void main(String[] args) {
        int number = 1;
        reassignPrimitive(number);
        System.out.println("after reassignPrimitive: " + number);

        Box box = new Box(1);
        reassignReference(box);
        System.out.println("after reassignReference: " + box);

        mutateThroughReference(box);
        System.out.println("after mutateThroughReference: " + box);

        int[] data = {1, 2, 3};
        reassignArray(data);
        System.out.println("after reassignArray: " + Arrays.toString(data));

        mutateArray(data);
        System.out.println("after mutateArray: " + Arrays.toString(data));

        System.out.println("summary: parameter slot is a copy; object/array identity may be shared");
    }
}
