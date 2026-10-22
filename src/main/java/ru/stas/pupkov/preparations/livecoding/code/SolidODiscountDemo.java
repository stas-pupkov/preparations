package ru.stas.pupkov.preparations.livecoding.code;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * OCP: новый тип клиента = новая {@link DiscountPolicy}, без правки ядра {@link DiscountService}.
 */
public class SolidODiscountDemo {

    public static void main(String[] args) {
        DiscountService service = new DiscountService(List.of(
                new PercentDiscount("Обычный", "0.95"),
                new PercentDiscount("ВИП", "0.80"),
                new PercentDiscount("Сотрудник", "0.50")
        ));

        BigDecimal sum = new BigDecimal("100.00");
        System.out.println("VIP → " + service.calculate(new Client("ВИП"), sum));
        System.out.println("Unknown → " + service.calculate(new Client("Партнёр"), sum));
    }

    record Client(String type) {
    }

    interface DiscountPolicy {
        boolean supports(Client client);

        BigDecimal apply(BigDecimal sum);
    }

    static final class PercentDiscount implements DiscountPolicy {
        private final String type;
        private final BigDecimal factor;

        PercentDiscount(String type, String factor) {
            this.type = type;
            this.factor = new BigDecimal(factor);
        }

        @Override
        public boolean supports(Client client) {
            return type.equals(client.type());
        }

        @Override
        public BigDecimal apply(BigDecimal sum) {
            return sum.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static final class DiscountService {
        private final List<DiscountPolicy> policies;

        DiscountService(List<DiscountPolicy> policies) {
            this.policies = List.copyOf(policies);
        }

        BigDecimal calculate(Client client, BigDecimal sum) {
            return policies.stream()
                    .filter(p -> p.supports(client))
                    .findFirst()
                    .orElse(NO_DISCOUNT)
                    .apply(sum);
        }
    }

    private static final DiscountPolicy NO_DISCOUNT = new DiscountPolicy() {
        @Override
        public boolean supports(Client client) {
            return true;
        }

        @Override
        public BigDecimal apply(BigDecimal sum) {
            return sum;
        }
    };
}
