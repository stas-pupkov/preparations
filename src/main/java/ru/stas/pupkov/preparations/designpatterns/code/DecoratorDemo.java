package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Decorator: динамически наращивает поведение обёртками с тем же (или расширенным) интерфейсом.
 *
 * Spring-якорь: {@link BeanPostProcessor} может обернуть бин; JDK I/O — канонический Decorator.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class DecoratorDemo {

    public static void main(String[] args) throws IOException {
        System.out.println("=== Classic Decorator (notifier channels) ===");
        Notifier n = new SmsDecorator(new SlackDecorator(new EmailNotifier()));
        n.send("disk 95%");

        System.out.println();
        System.out.println("=== JDK I/O Decorator ===");
        try (InputStream in = new UpperCaseInputStream(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))) {
            System.out.println("  " + new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        System.out.println();
        System.out.println("=== Spring BeanPostProcessor wraps bean ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            Greeter greeter = ctx.getBean(Greeter.class);
            System.out.println(greeter.greet("Stas"));
        }
    }

    interface Notifier {
        void send(String message);
    }

    static final class EmailNotifier implements Notifier {
        @Override
        public void send(String message) {
            System.out.println("  email: " + message);
        }
    }

    abstract static class NotifierDecorator implements Notifier {
        private final Notifier wrappee;

        NotifierDecorator(Notifier wrappee) {
            this.wrappee = wrappee;
        }

        @Override
        public void send(String message) {
            wrappee.send(message);
        }
    }

    static final class SmsDecorator extends NotifierDecorator {
        SmsDecorator(Notifier wrappee) {
            super(wrappee);
        }

        @Override
        public void send(String message) {
            super.send(message);
            System.out.println("  sms: " + message);
        }
    }

    static final class SlackDecorator extends NotifierDecorator {
        SlackDecorator(Notifier wrappee) {
            super(wrappee);
        }

        @Override
        public void send(String message) {
            super.send(message);
            System.out.println("  slack: " + message);
        }
    }

    static final class UpperCaseInputStream extends FilterInputStream {
        UpperCaseInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            return b == -1 ? -1 : Character.toUpperCase(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            for (int i = off; i < off + n; i++) {
                b[i] = (byte) Character.toUpperCase(b[i]);
            }
            return n;
        }
    }

    interface Greeter {
        String greet(String name);
    }

    static final class PlainGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    static final class LoggingGreeter implements Greeter {
        private final Greeter delegate;

        LoggingGreeter(Greeter delegate) {
            this.delegate = delegate;
        }

        @Override
        public String greet(String name) {
            String result = delegate.greet(name);
            System.out.println("  [log] greet(" + name + ") -> " + result);
            return result;
        }
    }

    static final class GreeterPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof Greeter g && !(bean instanceof LoggingGreeter)) {
                return new LoggingGreeter(g);
            }
            return bean;
        }
    }

    @Configuration
    static class Config {
        @Bean
        Greeter greeter() {
            return new PlainGreeter();
        }

        @Bean
        static GreeterPostProcessor greeterPostProcessor() {
            return new GreeterPostProcessor();
        }
    }
}
