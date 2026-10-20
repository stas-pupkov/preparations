package ru.stas.pupkov.preparations.spring.code;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Порядок lifecycle: Aware → BPP.before → @PostConstruct → BPP.after (часто тут появляется proxy).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class BeanLifecycleOrderDemo {

    public static void main(String[] args) {
        System.out.println("=== start context ===");
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        DemoBean bean = ctx.getBean(DemoBean.class);
        System.out.println("getBean class = " + bean.getClass().getName());
        bean.work();
        System.out.println("=== close context ===");
        ctx.close();
    }

    @Configuration
    static class Config {

        @Bean
        static BeanPostProcessor loggingBpp() {
            return new LoggingBpp();
        }

        @Bean
        DemoBean demoBean() {
            System.out.println("1. @Bean factory: instantiate DemoBean");
            return new DemoBean();
        }
    }

    static final class LoggingBpp implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof DemoBean) {
                System.out.println("3. BPP.postProcessBeforeInitialization (" + beanName + ")");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof DemoBean) {
                System.out.println("5. BPP.postProcessAfterInitialization (" + beanName + ") — типичное место для AOP-proxy");
            }
            return bean;
        }
    }

    static final class DemoBean {

        DemoBean() {
            System.out.println("2. constructor");
        }

        @PostConstruct
        void init() {
            System.out.println("4. @PostConstruct");
        }

        void work() {
            System.out.println("6. business call");
        }

        @PreDestroy
        void destroy() {
            System.out.println("7. @PreDestroy");
        }
    }
}
