package ru.stas.pupkov.preparations.oop.code;

/**
 * DIP (принцип дизайна) vs IoC (кто управляет потоком / сборкой графа) vs DI (механизм передачи зависимостей).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class DipVsIocDemo {

    public static void main(String[] args) {
        System.out.println("=== 1. Без DIP: high-level зависит от concrete low-level ===");
        new OrderServiceNoDip().place("order-1");

        System.out.println();
        System.out.println("=== 2. DIP соблюдён: оба зависят от абстракции ===");
        // Composition root: детали собираются снаружи use-case
        OrderServiceDip dipService = new OrderServiceDip(new SmtpNotifierAdapter());
        dipService.place("order-2");

        System.out.println();
        System.out.println("=== 3. IoC без DI-контейнера: Template Method вызывает ваш код ===");
        new CheckoutPipeline().run("order-3");

        System.out.println();
        System.out.println("=== 4. IoC + DI (ручной «контейнер»): граф собирает assembler, не use-case ===");
        OrderServiceDip wired = TinyContainer.wireOrderService();
        wired.place("order-4");

        System.out.println();
        System.out.println("=== 5. IoC есть, DIP нарушен: контейнер внедрил concrete ===");
        OrderServiceIocNoDip bad = new OrderServiceIocNoDip(new SmtpNotifier());
        bad.place("order-5");
    }

    // --- low-level detail ---

    static final class SmtpNotifier {
        void send(String msg) {
            System.out.println("  [SmtpNotifier] " + msg);
        }
    }

    // --- DIP: абстракция в «центре» ---

    interface Notifier {
        void notify(String msg);
    }

    static final class SmtpNotifierAdapter implements Notifier {
        private final SmtpNotifier smtp = new SmtpNotifier();

        @Override
        public void notify(String msg) {
            smtp.send(msg);
        }
    }

    // 1. DIP нарушен: OrderService знает SmtpNotifier
    static final class OrderServiceNoDip {
        private final SmtpNotifier notifier = new SmtpNotifier();

        void place(String id) {
            System.out.println("  place " + id);
            notifier.send("placed " + id);
        }
    }

    // 2. DIP: high-level зависит только от Notifier
    static final class OrderServiceDip {
        private final Notifier notifier;

        OrderServiceDip(Notifier notifier) {
            this.notifier = notifier;
        }

        void place(String id) {
            System.out.println("  place " + id);
            notifier.notify("placed " + id);
        }
    }

    // 3. IoC через Template Method: каркас вызывает hooks (Hollywood principle)
    abstract static class Pipeline {
        final void run(String id) {
            before(id);
            execute(id); // контроль у базового класса, не у вызывающего «детали»
            after(id);
        }

        protected void before(String id) {
            System.out.println("  before " + id);
        }

        protected abstract void execute(String id);

        protected void after(String id) {
            System.out.println("  after " + id);
        }
    }

    static final class CheckoutPipeline extends Pipeline {
        @Override
        protected void execute(String id) {
            System.out.println("  checkout execute " + id);
        }
    }

    // 4. Минимальный IoC-assembler (аналог того, что делает Spring ApplicationContext)
    static final class TinyContainer {
        static OrderServiceDip wireOrderService() {
            Notifier notifier = new SmtpNotifierAdapter();
            return new OrderServiceDip(notifier);
        }
    }

    // 5. DI/IoC механизмами есть, но тип зависимости — concrete → DIP нет
    static final class OrderServiceIocNoDip {
        private final SmtpNotifier notifier;

        OrderServiceIocNoDip(SmtpNotifier notifier) {
            this.notifier = notifier;
        }

        void place(String id) {
            System.out.println("  place " + id);
            notifier.send("placed " + id);
        }
    }
}
