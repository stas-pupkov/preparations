package ru.stas.pupkov.preparations.livecoding.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Модификация {@code ArrayList} внутри {@code stream().forEach}:
 * remove последнего → обычно CME; remove первого → часто NPE (null в хвосте массива), иначе CME.
 */
public class StreamForEachRemoveDemo {

    public static void main(String[] args) {
        run("remove C (last)", list -> list.stream().forEach(x -> {
            if (x.equals("C")) {
                list.remove(x);
            }
        }));

        run("remove A (first)", list -> list.stream().forEach(x -> {
            if (x.equals("A")) {
                list.remove(x);
            }
        }));

        List<String> ok = new ArrayList<>(Arrays.asList("A", "B", "C"));
        ok.removeIf("A"::equals);
        System.out.println("removeIf → " + ok);
    }

    private static void run(String title, java.util.function.Consumer<List<String>> action) {
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
        System.out.print(title + " → ");
        try {
            action.accept(list);
            System.out.println("OK list=" + list);
        } catch (RuntimeException ex) {
            System.out.println(ex.getClass().getSimpleName()
                    + (ex.getMessage() != null ? " (" + ex.getMessage() + ")" : ""));
        }
    }
}
