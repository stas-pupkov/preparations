package ru.stas.pupkov.preparations.oop.code;

import java.util.Objects;

/**
 * default / static / private методы в interface: роли и типичные применения.
 *
 * <ul>
 *   <li>{@code default} — Java 8: эволюция API + общая логика поверх abstract-хуков</li>
 *   <li>{@code static} — Java 8: фабрики/утилиты на типе (вызов через Interface.name)</li>
 *   <li>{@code private} / {@code private static} — Java 9: helpers без расширения публичного API</li>
 * </ul>
 */
public class InterfaceMethodKindsDemo {

    /**
     * Контракт доставки + default-обёртка + static-фабрика + private helpers.
     */
    interface Shipment {
        String destination();

        int weightKg();

        /** Эволюция API: старые реализации получают поведение «бесплатно». */
        default String label() {
            return formatLabel(destination(), weightKg());
        }

        /** Override точечно, когда нужен другой формат. */
        default String trackingHint() {
            return "track:" + normalizeCode(destination());
        }

        /** Фабрика на типе интерфейса — без отдельного Utils-класса. */
        static Shipment of(String destination, int weightKg) {
            Objects.requireNonNull(destination, "destination");
            if (weightKg <= 0) {
                throw new IllegalArgumentException("weightKg must be > 0");
            }
            return new SimpleShipment(normalizeCode(destination), weightKg);
        }

        /** private instance — общий код для нескольких default-методов. */
        private String formatLabel(String dest, int kg) {
            return normalizeCode(dest) + "#" + kg + "kg";
        }

        /** private static — доступен и из static, и из instance-методов интерфейса. */
        private static String normalizeCode(String raw) {
            return raw.trim().toUpperCase();
        }
    }

    record SimpleShipment(String destination, int weightKg) implements Shipment {
    }

    /** Пример conflict resolution при diamond из двух default. */
    interface Auditable {
        default String auditTag() {
            return "audit";
        }
    }

    interface Traceable {
        default String auditTag() {
            return "trace";
        }
    }

    static final class LoggedShipment implements Shipment, Auditable, Traceable {
        private final Shipment delegate;

        LoggedShipment(Shipment delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public String destination() {
            return delegate.destination();
        }

        @Override
        public int weightKg() {
            return delegate.weightKg();
        }

        @Override
        public String trackingHint() {
            return "LOGGED/" + Shipment.super.trackingHint();
        }

        /**
         * Обязательный override: оба супер-интерфейса дают default с одной сигнатурой.
         * Явный выбор через Interface.super.method().
         */
        @Override
        public String auditTag() {
            return Auditable.super.auditTag() + "+" + Traceable.super.auditTag();
        }
    }

    public static void main(String[] args) {
        Shipment s = Shipment.of("  berlin ", 12);
        System.out.println("destination=" + s.destination());
        System.out.println("label=" + s.label());
        System.out.println("hint=" + s.trackingHint());

        LoggedShipment logged = new LoggedShipment(s);
        System.out.println("loggedHint=" + logged.trackingHint());
        System.out.println("auditTag=" + logged.auditTag());

        // static интерфейса не наследуется через экземпляр — только Interface.name
        // logged.of(...); // не скомпилируется
        Shipment again = Shipment.of("paris", 3);
        System.out.println("again=" + again.label());
    }
}
