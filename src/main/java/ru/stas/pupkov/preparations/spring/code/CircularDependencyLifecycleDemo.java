package ru.stas.pupkov.preparations.spring.code;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.MethodMetadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Циклическая зависимость + почему бизнес-логику нельзя класть в {@code @PostConstruct}:
 * соседний бин уже есть как объект, но его init может быть ещё не завершён.
 * Отложенный старт — на {@link ContextRefreshedEvent} (идея {@code @RunAfterStart} из spring-beans/circling).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class CircularDependencyLifecycleDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        System.out.println("--- контекст готов, бины инициализированы ---");
        ctx.close();
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface RunAfterStart {
    }

    static class Husband {

        @Autowired
        private Wife wife;

        private String money;

        Husband() {
            System.out.println("Husband ctor");
        }

        @PostConstruct
        void init() {
            System.out.println("Husband @PostConstruct → money=5000$");
            money = "5000$";
            // если здесь вызвать wife.getCompliment() — при неудачном порядке возможен NPE:
            // Wife ещё не прошла свой @PostConstruct
        }

        @RunAfterStart
        public void afterContextReady() {
            System.out.println("Husband @RunAfterStart: "
                    + wife.getCompliment().toUpperCase()
                    + " > "
                    + money);
        }

        String getMoney() {
            return money;
        }
    }

    static class Wife {

        @Autowired
        private Husband husband;

        private String compliment;

        Wife() {
            System.out.println("Wife ctor");
        }

        @PostConstruct
        void init() {
            System.out.println("Wife @PostConstruct → compliment=love");
            compliment = "love";
        }

        @RunAfterStart
        public void afterContextReady() {
            System.out.println("Wife @RunAfterStart: "
                    + husband.getMoney().toUpperCase()
                    + " is nothing vs "
                    + compliment);
        }

        String getCompliment() {
            return compliment;
        }
    }

    static class RunAfterStartListener {

        @Autowired
        private ConfigurableListableBeanFactory factory;

        @EventListener
        public void onRefresh(ContextRefreshedEvent event) throws Exception {
            System.out.println("[ContextRefreshedEvent] вызываю @RunAfterStart");
            ApplicationContext context = event.getApplicationContext();
            for (String name : factory.getBeanDefinitionNames()) {
                BeanDefinition bd = factory.getBeanDefinition(name);
                String className = resolveClassName(bd);
                if (className == null) {
                    continue;
                }
                Class<?> type = Class.forName(className);
                for (Method method : type.getMethods()) {
                    if (!method.isAnnotationPresent(RunAfterStart.class)) {
                        continue;
                    }
                    Object bean = context.getBean(name);
                    bean.getClass()
                            .getMethod(method.getName(), method.getParameterTypes())
                            .invoke(bean);
                }
            }
        }

        private static String resolveClassName(BeanDefinition bd) {
            if (bd.getBeanClassName() != null) {
                return bd.getBeanClassName();
            }
            if (bd instanceof AnnotatedBeanDefinition annotated) {
                MethodMetadata metadata = annotated.getFactoryMethodMetadata();
                if (metadata != null) {
                    return metadata.getReturnTypeName();
                }
            }
            return null;
        }
    }

    @Configuration
    static class Config {

        @Bean
        Husband husband() {
            return new Husband();
        }

        @Bean
        Wife wife() {
            return new Wife();
        }

        @Bean
        RunAfterStartListener runAfterStartListener() {
            return new RunAfterStartListener();
        }
    }
}
