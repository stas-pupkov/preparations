package ru.stas.pupkov.preparations.java8.code;

import java.util.List;
import java.util.Optional;

/**
 * Три формы Stream.reduce и зачем нужна ассоциативность в parallel.
 */
public class ReduceDemo {

    public static void main(String[] args) {
        List<Integer> nums = List.of(1, 2, 3, 4);

        System.out.println("--- 1) reduce(identity, accumulator) ---");
        int sum = nums.stream().reduce(0, Integer::sum);
        System.out.println("sum=" + sum); // 10

        int emptySum = List.<Integer>of().stream().reduce(0, Integer::sum);
        System.out.println("empty sum=" + emptySum); // 0 — identity

        System.out.println("--- 2) reduce(accumulator) → Optional ---");
        Optional<Integer> maybeSum = nums.stream().reduce(Integer::sum);
        Optional<Integer> empty = List.<Integer>of().stream().reduce(Integer::sum);
        System.out.println("maybeSum=" + maybeSum + ", empty=" + empty);

        System.out.println("--- 3) reduce(identity, accumulator, combiner) ---");
        // тип результата может отличаться от типа элемента
        String joined = nums.stream().reduce(
                "",
                (acc, n) -> acc + n,           // U × T → U
                (left, right) -> left + right  // U × U → U (для parallel)
        );
        System.out.println("joined=" + joined);

        System.out.println("--- parallel: ассоциативная сумма ок ---");
        int parallelSum = nums.parallelStream().reduce(0, Integer::sum);
        System.out.println("parallelSum=" + parallelSum);

        System.out.println("--- вычитание НЕ ассоциативно: sequential vs parallel ---");
        List<Integer> sub = List.of(10, 3, 2, 1);
        int sequential = sub.stream().reduce(0, (a, b) -> a - b);
        int parallel = sub.parallelStream().reduce(0, (a, b) -> a - b);
        System.out.println("sequential=" + sequential + ", parallel=" + parallel
                + " (могут различаться; оба «подозрительны» для минуса)");
    }
}
