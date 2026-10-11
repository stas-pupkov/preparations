package ru.stas.pupkov.preparations.java8.code;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * java.time: Instant / Local* / ZonedDateTime и TemporalAdjusters.
 */
public class DateTimeApiDemo {

    public static void main(String[] args) {
        LocalDate today = LocalDate.now();
        System.out.println("today: " + today);
        System.out.println("+1 week: " + today.plusWeeks(1));
        System.out.println("+1 month: " + today.plusMonths(1));
        System.out.println("+1 year: " + today.plusYears(1));
        System.out.println("+1 decade: " + today.plus(1, ChronoUnit.DECADES));

        LocalDate nextTuesday = today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        LocalDate secondSaturday = today.with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SATURDAY));
        System.out.println("next Tuesday: " + nextTuesday);
        System.out.println("2nd Saturday this month: " + secondSaturday);

        Instant instant = Instant.now();
        System.out.println("Instant.now(): " + instant);

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime local = LocalDateTime.ofInstant(instant, zone);
        ZonedDateTime zoned = ZonedDateTime.ofInstant(instant, ZoneId.of("Europe/Moscow"));
        System.out.println("LocalDateTime in " + zone + ": " + local);
        System.out.println("ZonedDateTime Europe/Moscow: " + zoned);
    }
}
