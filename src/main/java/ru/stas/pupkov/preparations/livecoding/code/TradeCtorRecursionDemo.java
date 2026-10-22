package ru.stas.pupkov.preparations.livecoding.code;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code Trade} ctor → {@code TradeCancel} ctor → {@code Trade} ctor → {@code StackOverflowError}.
 */
public class TradeCtorRecursionDemo {

    public static void main(String[] args) {
        try {
            Trade trade = new Trade(BigDecimal.valueOf(1234.78), LocalDate.now());
            TradeCancel cancel = trade.getCancel();
            System.out.println("unexpected success: " + cancel);
        } catch (StackOverflowError e) {
            System.out.println("StackOverflowError: взаимная рекурсия конструкторов Trade ↔ TradeCancel");
        }
    }

    static final class Trade {
        private final BigDecimal cost;
        private final LocalDate date;
        private final TradeCancel cancel;

        Trade(BigDecimal cost, LocalDate date) {
            this.cost = cost;
            this.date = date;
            this.cancel = new TradeCancel(cost, date);
        }

        TradeCancel getCancel() {
            return cancel;
        }
    }

    static final class TradeCancel {
        private final BigDecimal cost;
        private final LocalDate date;
        private final Trade primaryTrade;

        TradeCancel(BigDecimal cost, LocalDate date) {
            this.cost = cost.negate();
            this.date = date.plusDays(1);
            this.primaryTrade = new Trade(cost, date);
        }
    }
}
