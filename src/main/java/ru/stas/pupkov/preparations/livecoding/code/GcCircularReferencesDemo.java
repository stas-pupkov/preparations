package ru.stas.pupkov.preparations.livecoding.code;

import java.lang.ref.WeakReference;

/**
 * Цикл A↔B не удерживает объекты от GC, если на них нет пути от GC roots.
 * {@code System.gc()} — hint; weak refs используются только для иллюстрации.
 */
public class GcCircularReferencesDemo {

    public static void main(String[] args) throws InterruptedException {
        A objA = new A();
        B objB = new B();
        objA.b = objB;
        objB.a = objA;

        WeakReference<A> weakA = new WeakReference<>(objA);
        WeakReference<B> weakB = new WeakReference<>(objB);

        objA = null;
        objB = null;

        System.gc();
        Thread.sleep(50);

        System.out.println("weakA cleared: " + (weakA.get() == null));
        System.out.println("weakB cleared: " + (weakB.get() == null));
        System.out.println("(оба true ожидаемы после сборки; GC не гарантирован System.gc())");
    }

    static final class A {
        B b;
    }

    static final class B {
        A a;
    }
}
