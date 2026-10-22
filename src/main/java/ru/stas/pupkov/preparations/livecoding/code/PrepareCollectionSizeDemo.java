package ru.stas.pupkov.preparations.livecoding.code;

import java.util.HashSet;
import java.util.Set;

/**
 * Дефолтные {@code equals}/{@code hashCode} (identity) → {@code prepareCollection()} = 3.
 */
public class PrepareCollectionSizeDemo {

    public static void main(String[] args) {
        int size = prepareCollection();
        System.out.println("prepareCollection() = " + size);
        if (size != 3) {
            throw new AssertionError("expected 3, got " + size);
        }
    }

    public static int prepareCollection() {
        Set<A> set = new HashSet<>();
        set.add(new A());
        set.add(new B());
        set.add(new B());
        return set.size();
    }

    public static class A {
    }

    public static class B extends A {
    }
}
