package ru.stas.pupkov.preparations.core.code;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * Static nested vs inner (захват {@code this$0}), local/anonymous и effectively final.
 *
 * <p>С Java 16 inner-классы могут объявлять {@code static} members — старое «нельзя» устарело.
 */
public class NestedClassesCaptureDemo {

    private final String outerLabel = "outer";

    static final class StaticNested {
        String describe() {
            // нет неявной ссылки на Outer
            return "StaticNested, no Outer.this";
        }
    }

    final class Inner {
        // Java 16+: static member внутри inner допустим
        static final String KIND = "inner";

        String describe() {
            return "Inner sees " + NestedClassesCaptureDemo.this.outerLabel + ", KIND=" + KIND;
        }
    }

    Supplier<String> localCapture(String prefix) {
        // с Java 8 достаточно effectively final — явное final не обязательно
        String tag = prefix + "-local";
        class Local {
            String describe() {
                return tag + " + " + outerLabel;
            }
        }
        return () -> new Local().describe();
    }

    Supplier<String> anonymousCapture() {
        return new Supplier<>() {
            @Override
            public String get() {
                return "anon + " + outerLabel;
            }
        };
    }

    public static void main(String[] args) throws Exception {
        NestedClassesCaptureDemo outer = new NestedClassesCaptureDemo();

        System.out.println("--- static nested ---");
        StaticNested nested = new StaticNested();
        System.out.println(nested.describe());
        System.out.println("synthetic Outer field on StaticNested?: "
                + hasSyntheticOuterField(StaticNested.class));

        System.out.println("--- inner holds Outer.this ---");
        Inner inner = outer.new Inner();
        System.out.println(inner.describe());
        System.out.println("synthetic Outer field on Inner?: "
                + hasSyntheticOuterField(Inner.class));

        System.out.println("--- local / anonymous, effectively final ---");
        System.out.println(outer.localCapture("demo").get());
        System.out.println(outer.anonymousCapture().get());

        System.out.println("--- mutating captured local would not compile ---");
        System.out.println("(commented) int n = 0; n++; Supplier s = () -> n; // not effectively final");
    }

    private static boolean hasSyntheticOuterField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic() && field.getName().startsWith("this$")) {
                return true;
            }
        }
        return false;
    }
}
