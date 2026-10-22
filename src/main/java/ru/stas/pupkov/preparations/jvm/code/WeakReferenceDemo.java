package ru.stas.pupkov.preparations.jvm.code;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Strong держит объект; WeakReference / WeakHashMap — нет.
 * {@code System.gc()} — только намёк, на собесе это подчёркивать.
 */
public class WeakReferenceDemo {

    public static void main(String[] args) throws InterruptedException {
        Object strong = new Object();
        WeakReference<Object> weak = new WeakReference<>(strong);

        System.out.println("with strong: weak.get()=" + (weak.get() != null));

        strong = null;
        System.gc();
        Thread.sleep(50);
        System.out.println("after clear strong + gc hint: weak.get()=" + (weak.get() != null)
                + " (может ещё быть non-null — GC не гарантирован)");

        WeakHashMap<Object, String> map = new WeakHashMap<>();
        Object key = new Object();
        map.put(key, "meta");
        System.out.println("WeakHashMap size with strong key=" + map.size());
        key = null;
        System.gc();
        Thread.sleep(50);
        System.out.println("WeakHashMap size after key gone≈" + map.size()
                + " (часто 0 после GC)");
    }
}
