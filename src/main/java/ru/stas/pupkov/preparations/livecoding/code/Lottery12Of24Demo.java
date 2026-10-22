package ru.stas.pupkov.preparations.livecoding.code;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Лотерея 12 из 24: билет 100₽, выплаты по числу совпадений (симметрия 0↔12, …, 4↔8).
 * Доп*: баланс 10_000, пока не 0 (или &lt; цены билета) либо джекпот 1_000_000 за тираж.
 */
public class Lottery12Of24Demo {

    private static final int POOL_SIZE = 24;
    private static final int PICK = 12;
    private static final int TICKET_PRICE = 100;
    private static final int JACKPOT = 1_000_000;

    public static void main(String[] args) {
        Set<Integer> ticket = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        DrawResult one = playRound(ticket);
        System.out.println("Один тираж: matches=" + one.matches()
                + " prize=" + one.prize()
                + " draw=" + one.draw());

        SimulationResult sim = simulateUntilBrokeOrJackpot(10_000, ticket);
        System.out.println("Симуляция: rounds=" + sim.rounds()
                + " finalBalance=" + sim.finalBalance()
                + " jackpotHit=" + sim.jackpotHit()
                + " reason=" + sim.stopReason());
    }

    static DrawResult playRound(Set<Integer> ticket) {
        validateTicket(ticket);
        Set<Integer> draw = drawNumbers();
        int matches = intersectionSize(ticket, draw);
        return new DrawResult(draw, matches, prizeFor(matches));
    }

    static SimulationResult simulateUntilBrokeOrJackpot(int startBalance, Set<Integer> ticket) {
        validateTicket(ticket);
        int balance = startBalance;
        int rounds = 0;
        while (balance >= TICKET_PRICE) {
            balance -= TICKET_PRICE;
            DrawResult r = playRound(ticket);
            balance += r.prize();
            rounds++;
            if (r.prize() >= JACKPOT) {
                return new SimulationResult(rounds, balance, true, "jackpot");
            }
        }
        return new SimulationResult(rounds, balance, false, "broke");
    }

    static int prizeFor(int matches) {
        int key = Math.min(matches, PICK - matches);
        return switch (key) {
            case 0 -> JACKPOT;
            case 1 -> 50_000;
            case 2 -> 5_000;
            case 3 -> 500;
            case 4 -> 100;
            default -> 0; // 5,6,7
        };
    }

    static Set<Integer> drawNumbers() {
        List<Integer> pool = IntStream.rangeClosed(1, POOL_SIZE).boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(pool, ThreadLocalRandom.current());
        return Set.copyOf(pool.subList(0, PICK));
    }

    static void validateTicket(Set<Integer> ticket) {
        if (ticket.size() != PICK) {
            throw new IllegalArgumentException("need exactly " + PICK + " unique numbers");
        }
        for (int n : ticket) {
            if (n < 1 || n > POOL_SIZE) {
                throw new IllegalArgumentException("out of range: " + n);
            }
        }
    }

    static int intersectionSize(Set<Integer> a, Set<Integer> b) {
        Set<Integer> copy = new HashSet<>(a);
        copy.retainAll(b);
        return copy.size();
    }

    record DrawResult(Set<Integer> draw, int matches, int prize) {
    }

    record SimulationResult(int rounds, int finalBalance, boolean jackpotHit, String stopReason) {
    }
}
