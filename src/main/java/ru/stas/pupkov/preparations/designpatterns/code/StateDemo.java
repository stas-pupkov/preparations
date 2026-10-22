package ru.stas.pupkov.preparations.designpatterns.code;

/**
 * State: поведение объекта меняется вместе с внутренним состоянием (FSM).
 *
 * Spring-якорь: явного State в контейнере нет; типичный прод-кейс — статусы заказа/платежа
 * как state machine (Spring Statemachine или свой enum+переходы).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class StateDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic State (document workflow) ===");
        Document doc = new Document();
        doc.render();
        doc.publish();
        doc.render();
        doc.publish();
        doc.render();

        System.out.println();
        System.out.println("=== Order status machine (Spring-domain style) ===");
        Order order = new Order("o-1");
        System.out.println(order);
        order.pay();
        System.out.println(order);
        order.ship();
        System.out.println(order);
        try {
            order.pay();
        } catch (IllegalStateException ex) {
            System.out.println("  expected: " + ex.getMessage());
        }
    }

    interface State {
        void publish(Document ctx);

        void render(Document ctx);
    }

    static final class Draft implements State {
        @Override
        public void publish(Document ctx) {
            ctx.changeState(new Moderation());
            System.out.println("  Draft -> Moderation");
        }

        @Override
        public void render(Document ctx) {
            System.out.println("  render draft");
        }
    }

    static final class Moderation implements State {
        @Override
        public void publish(Document ctx) {
            ctx.changeState(new Published());
            System.out.println("  Moderation -> Published");
        }

        @Override
        public void render(Document ctx) {
            System.out.println("  render moderation preview");
        }
    }

    static final class Published implements State {
        @Override
        public void publish(Document ctx) {
            System.out.println("  already published");
        }

        @Override
        public void render(Document ctx) {
            System.out.println("  render published");
        }
    }

    static final class Document {
        private State state = new Draft();

        void changeState(State state) {
            this.state = state;
        }

        void publish() {
            state.publish(this);
        }

        void render() {
            state.render(this);
        }
    }

    enum OrderStatus {
        NEW, PAID, SHIPPED
    }

    static final class Order {
        private final String id;
        private OrderStatus status = OrderStatus.NEW;

        Order(String id) {
            this.id = id;
        }

        void pay() {
            if (status != OrderStatus.NEW) {
                throw new IllegalStateException("cannot pay from " + status);
            }
            status = OrderStatus.PAID;
        }

        void ship() {
            if (status != OrderStatus.PAID) {
                throw new IllegalStateException("cannot ship from " + status);
            }
            status = OrderStatus.SHIPPED;
        }

        @Override
        public String toString() {
            return "Order(" + id + ", " + status + ")";
        }
    }
}
