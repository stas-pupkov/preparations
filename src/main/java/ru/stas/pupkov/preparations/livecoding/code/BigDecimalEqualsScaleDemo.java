package ru.stas.pupkov.preparations.livecoding.code;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code BigDecimal.equals} учитывает scale: 10.0 ≠ 10.00, хотя {@code compareTo == 0}.
 */
public class BigDecimalEqualsScaleDemo {

    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("10.0");
        BigDecimal b = new BigDecimal("10.00");

        System.out.println("equals=" + a.equals(b));
        System.out.println("compareTo=" + a.compareTo(b));
        System.out.println("scales=" + a.scale() + "," + b.scale());

        Set<BigDecimal> set = new HashSet<>();
        set.add(a);
        set.add(b);
        System.out.println("HashSet size=" + set.size() + " (ожидаем 2)");
    }
}
