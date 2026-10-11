package ru.stas.pupkov.preparations.oop.code;

/**
 * Роль интерфейса vs каркас в abstract class (template method).
 */
public class AbstractVsInterfaceDemo {

    interface Notifier {
        void send(String message);
    }

    static final class EmailNotifier implements Notifier {
        @Override
        public void send(String message) {
            System.out.println("email: " + message);
        }
    }

    static final class SmsNotifier implements Notifier {
        @Override
        public void send(String message) {
            System.out.println("sms: " + message);
        }
    }

    abstract static class ReportJob {
        final void run() {
            String data = load();
            String rendered = render(data);
            publish(rendered);
        }

        protected abstract String load();

        protected abstract String render(String data);

        protected void publish(String rendered) {
            System.out.println("publish: " + rendered);
        }
    }

    static final class DailySalesReport extends ReportJob {
        @Override
        protected String load() {
            return "sales=42";
        }

        @Override
        protected String render(String data) {
            return "Daily[" + data + "]";
        }
    }

    public static void main(String[] args) {
        Notifier notifier = new EmailNotifier();
        notifier.send("hello");
        notifier = new SmsNotifier();
        notifier.send("hello");

        new DailySalesReport().run();
    }
}
