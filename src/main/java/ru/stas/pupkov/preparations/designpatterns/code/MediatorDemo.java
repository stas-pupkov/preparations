package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Mediator: компоненты общаются через посредника, а не напрямую друг с другом.
 *
 * Spring-якорь: {@link ApplicationEventPublisher} как шина — компоненты знают только о событиях,
 * не о конкретных подписчиках (ослабленный Mediator / Observer hybrid).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class MediatorDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Mediator (dialog) ===");
        AuthenticationDialog dialog = new AuthenticationDialog();
        dialog.simulateUserChoosesLogin();
        dialog.simulateUserChoosesRegister();

        System.out.println();
        System.out.println("=== Spring events as mediator bus ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            ctx.getBean(CheckoutOrchestrator.class).checkout("cart-1");
        }
    }

    interface Mediator {
        void notify(Component sender, String event);
    }

    abstract static class Component {
        protected Mediator dialog;

        void setMediator(Mediator dialog) {
            this.dialog = dialog;
        }
    }

    static final class Checkbox extends Component {
        private boolean checked;

        void check() {
            checked = true;
            dialog.notify(this, "check");
        }

        boolean isChecked() {
            return checked;
        }
    }

    static final class Button extends Component {
        void click() {
            dialog.notify(this, "click");
        }
    }

    static final class Textbox extends Component {
        private String text = "";

        void setText(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    static final class AuthenticationDialog implements Mediator {
        private final Checkbox loginOrRegister = new Checkbox();
        private final Textbox login = new Textbox();
        private final Textbox password = new Textbox();
        private final Button ok = new Button();

        AuthenticationDialog() {
            loginOrRegister.setMediator(this);
            login.setMediator(this);
            password.setMediator(this);
            ok.setMediator(this);
        }

        void simulateUserChoosesLogin() {
            login.setText("user");
            password.setText("secret");
            ok.click();
        }

        void simulateUserChoosesRegister() {
            loginOrRegister.check();
            login.setText("new-user");
            password.setText("secret");
            ok.click();
        }

        @Override
        public void notify(Component sender, String event) {
            if (sender == loginOrRegister && "check".equals(event)) {
                System.out.println("  UI: switch to register form");
            }
            if (sender == ok && "click".equals(event)) {
                if (loginOrRegister.isChecked()) {
                    System.out.println("  register " + login.text());
                } else {
                    System.out.println("  login " + login.text());
                }
            }
        }
    }

    record CheckoutRequested(String cartId) {
    }

    record PaymentCompleted(String cartId) {
    }

    static final class CheckoutOrchestrator {
        private final ApplicationEventPublisher publisher;

        CheckoutOrchestrator(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        void checkout(String cartId) {
            publisher.publishEvent(new CheckoutRequested(cartId));
        }
    }

    static final class InventoryListener {
        private final ApplicationEventPublisher publisher;

        InventoryListener(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @EventListener
        void on(CheckoutRequested event) {
            System.out.println("  reserve stock for " + event.cartId());
            publisher.publishEvent(new PaymentCompleted(event.cartId()));
        }
    }

    static final class PaymentListener {
        @EventListener
        void on(PaymentCompleted event) {
            System.out.println("  charge card for " + event.cartId());
        }
    }

    @Configuration
    static class Config {
        @Bean
        CheckoutOrchestrator checkoutOrchestrator(ApplicationEventPublisher publisher) {
            return new CheckoutOrchestrator(publisher);
        }

        @Bean
        InventoryListener inventoryListener(ApplicationEventPublisher publisher) {
            return new InventoryListener(publisher);
        }

        @Bean
        PaymentListener paymentListener() {
            return new PaymentListener();
        }
    }
}
