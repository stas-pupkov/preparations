package ru.stas.pupkov.preparations.spring.code;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Singleton «замораживает» prototype при обычном inject. ObjectProvider даёт новый экземпляр.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class SingletonPrototypePitfallDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        BrokenClient broken = ctx.getBean(BrokenClient.class);
        FixedClient fixed = ctx.getBean(FixedClient.class);

        System.out.println("=== BrokenClient: prototype injected once into singleton ===");
        System.out.println("call1 id=" + broken.use().id());
        System.out.println("call2 id=" + broken.use().id());

        System.out.println();
        System.out.println("=== FixedClient: ObjectProvider.getObject() each time ===");
        System.out.println("call1 id=" + fixed.use().id());
        System.out.println("call2 id=" + fixed.use().id());

        ctx.close();
    }

    @Configuration
    static class Config {

        private int seq;

        @Bean
        @Scope("prototype")
        PrototypeToken token() {
            return new PrototypeToken(++seq);
        }

        @Bean
        BrokenClient brokenClient() {
            return new BrokenClient();
        }

        @Bean
        FixedClient fixedClient() {
            return new FixedClient();
        }
    }

    record PrototypeToken(int id) {
    }

    static final class BrokenClient {

        @Autowired
        private PrototypeToken token;

        PrototypeToken use() {
            return token;
        }
    }

    static final class FixedClient {

        @Autowired
        private ObjectProvider<PrototypeToken> tokens;

        PrototypeToken use() {
            return tokens.getObject();
        }
    }
}
