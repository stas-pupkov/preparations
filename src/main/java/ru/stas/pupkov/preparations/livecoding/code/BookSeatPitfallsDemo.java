package ru.stas.pupkov.preparations.livecoding.code;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Две частые ошибки из {@code bookSeat}: {@code ==} для тарифа и {@code double} для денег.
 */
public class BookSeatPitfallsDemo {

    public static void main(String[] args) {
        String tariffFromApi = new String("PREMIUM"); // как после десериализации
        System.out.println("tariff == \"PREMIUM\": " + (tariffFromApi == "PREMIUM"));
        System.out.println("\"PREMIUM\".equals(tariff): " + "PREMIUM".equals(tariffFromApi));

        double base = 10.0;
        double withDouble = base * 0.1d;
        System.out.println("double 10 * 0.1 = " + withDouble);

        BigDecimal price = new BigDecimal("10.00")
                .multiply(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);
        System.out.println("BigDecimal 10.00 * 0.10 = " + price);
    }
}
