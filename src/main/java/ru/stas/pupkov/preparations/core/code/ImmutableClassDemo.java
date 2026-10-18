package ru.stas.pupkov.preparations.core.code;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Как собрать immutable-класс: final + private final + copy in/out; record + List.copyOf.
 */
public class ImmutableClassDemo {

    public static final class Money {
        private final BigDecimal amount;
        private final Currency currency;

        public Money(BigDecimal amount, Currency currency) {
            this.amount = Objects.requireNonNull(amount);
            this.currency = Objects.requireNonNull(currency);
        }

        public Money plus(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("currency mismatch");
            }
            return new Money(amount.add(other.amount), currency);
        }

        public BigDecimal amount() {
            return amount;
        }

        public Currency currency() {
            return currency;
        }

        @Override
        public String toString() {
            return amount + " " + currency.getCurrencyCode();
        }
    }

    public static final class Order {
        private final List<String> items;

        public Order(List<String> items) {
            this.items = List.copyOf(items);
        }

        public List<String> items() {
            return items;
        }
    }

    public record MoneyRecord(BigDecimal amount, Currency currency) {
        public MoneyRecord {
            Objects.requireNonNull(amount);
            Objects.requireNonNull(currency);
        }

        public MoneyRecord plus(MoneyRecord other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("currency mismatch");
            }
            return new MoneyRecord(amount.add(other.amount), currency);
        }
    }

    public static void main(String[] args) {
        Currency usd = Currency.getInstance("USD");
        Money m1 = new Money(new BigDecimal("10.00"), usd);
        Money m2 = m1.plus(new Money(new BigDecimal("2.50"), usd));
        System.out.println("immutable Money: " + m1 + " + 2.50 → " + m2 + " (m1 не изменился)");

        List<String> raw = new ArrayList<>(List.of("a", "b"));
        Order order = new Order(raw);
        raw.add("hack");
        System.out.println("после мутации входного list: order.items=" + order.items());
        try {
            order.items().add("nope");
            System.out.println("ERROR: list should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            System.out.println("items() unmodifiable: " + expected.getClass().getSimpleName());
        }

        System.out.println();
        System.out.println("final List ref ≠ immutable:");
        final List<String> notImmutable = new ArrayList<>();
        notImmutable.add("x");
        System.out.println("final list after add: " + notImmutable);

        MoneyRecord r = new MoneyRecord(new BigDecimal("1"), usd).plus(
                new MoneyRecord(new BigDecimal("1"), usd));
        System.out.println("record Money: " + r);
    }
}
