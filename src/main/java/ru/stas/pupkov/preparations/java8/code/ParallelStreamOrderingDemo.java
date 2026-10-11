package ru.stas.pupkov.preparations.java8.code;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

/**
 * Порядок в parallel-stream и то, что побеждает последний parallel()/sequential().
 */
public class ParallelStreamOrderingDemo {

    public static void main(String[] args) {
        List<Integer> forEachOrder = new CopyOnWriteArrayList<>();
        IntStream.rangeClosed(1, 8)
                .parallel()
                .forEach(forEachOrder::add);
        System.out.println("parallel forEach (order not guaranteed): " + forEachOrder);

        List<Integer> ordered = new ArrayList<>();
        IntStream.rangeClosed(1, 8)
                .parallel()
                .forEachOrdered(ordered::add);
        System.out.println("parallel forEachOrdered: " + ordered);

        boolean endsSequential = IntStream.range(0, 100)
                .parallel()
                .map(x -> x * 2)
                .sequential()
                .isParallel();
        System.out.println("after .parallel().map().sequential() isParallel=" + endsSequential);

        boolean endsParallel = IntStream.range(0, 100)
                .sequential()
                .map(x -> x * 2)
                .parallel()
                .isParallel();
        System.out.println("after .sequential().map().parallel() isParallel=" + endsParallel);
    }
}
