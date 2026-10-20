package ru.stas.pupkov.preparations.spring.code;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Этапы инициализации контекста / бина (идеи из проекта spring-beans/ripper):
 * <pre>
 *   0. static @Bean → BeanFactoryPostProcessor / BeanPostProcessor (инфраструктура)
 *   1. constructor          — полей ещё нет (фаза 1)
 *   2. populate + BPP.before — кастомный inject
 *   3. @PostConstruct       — фаза 2
 *   4. BPP.after            — JDK-прокси
 *   5. ContextRefreshedEvent — фаза 3: вызов уже через прокси
 * </pre>
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class ContextInitPhasesDemo {

    public static void main(String[] args) {
        System.out.println("########## AnnotationConfigApplicationContext(Config) ##########");
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println();
        System.out.println("########## бизнес-вызов через прокси ##########");
        Quoter quoter = ctx.getBean(Quoter.class);
        System.out.println("getBean class = " + quoter.getClass().getName());
        quoter.sayQuote();

        ctx.close();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface InjectRandomInt {
        int min() default 2;
        int max() default 4;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Profiling {
    }

    /** Маркер «вызвать после ContextRefreshed, уже на прокси». */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface AfterProxy {
    }

    interface Quoter {
        void sayQuote();

        void afterProxyPhase();
    }

    @Profiling
    static class TerminatorQuoter implements Quoter {

        @InjectRandomInt(min = 2, max = 3)
        private int repeat;

        private final String message;

        TerminatorQuoter(String message) {
            this.message = message;
            System.out.println("[фаза 1] constructor: repeat=" + repeat + " (ещё default 0)");
        }

        @PostConstruct
        void init() {
            System.out.println("[фаза 2] @PostConstruct: repeat=" + repeat + " (уже после BPP.before)");
        }

        @AfterProxy
        @Override
        public void afterProxyPhase() {
            System.out.println("[фаза 3] @AfterProxy: вызов после ContextRefreshed, class="
                    + getClass().getName());
        }

        @Override
        public void sayQuote() {
            for (int i = 0; i < repeat; i++) {
                System.out.println("  quote: " + message);
            }
        }
    }

    /** BPP.before: кастомная «инъекция» в поле до @PostConstruct. */
    static final class InjectRandomIntBpp implements BeanPostProcessor {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (!(bean instanceof TerminatorQuoter)) {
                return bean;
            }
            System.out.println("[BPP.before] InjectRandomInt для " + beanName);
            for (Field field : bean.getClass().getDeclaredFields()) {
                InjectRandomInt ann = field.getAnnotation(InjectRandomInt.class);
                if (ann == null) {
                    continue;
                }
                int value = ThreadLocalRandom.current().nextInt(ann.min(), ann.max() + 1);
                field.setAccessible(true);
                ReflectionUtils.setField(field, bean, value);
            }
            return bean;
        }
    }

    /**
     * BPP: в before запоминаем класс с {@code @Profiling}, в after — JDK-прокси.
     * Аннотацию нельзя надёжно читать в after, если до нас уже обернули в чужой proxy.
     */
    static final class ProfilingBpp implements BeanPostProcessor {

        private final Map<String, Class<?>> profilingBeans = new HashMap<>();

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean.getClass().isAnnotationPresent(Profiling.class)) {
                profilingBeans.put(beanName, bean.getClass());
                System.out.println("[BPP.before] запомнил @Profiling для " + beanName);
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            Class<?> targetClass = profilingBeans.get(beanName);
            if (targetClass == null) {
                return bean;
            }
            System.out.println("[BPP.after] создаю JDK-прокси для " + beanName);
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(bean, args);
                }
                System.out.println("  [proxy] >>> " + method.getName());
                Object result = method.invoke(bean, args);
                System.out.println("  [proxy] <<< " + method.getName());
                return result;
            };
            return Proxy.newProxyInstance(
                    targetClass.getClassLoader(),
                    targetClass.getInterfaces(),
                    handler);
        }
    }

    /**
     * После refresh контекста: ищем {@code @AfterProxy} на исходном классе
     * (не на proxy.class) и вызываем через getBean → прокси.
     */
    static final class AfterProxyInvoker implements ApplicationListener<ContextRefreshedEvent> {

        @Autowired
        private ConfigurableListableBeanFactory factory;

        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            System.out.println("[ContextRefreshedEvent] контекст поднят, запускаю @AfterProxy");
            for (String name : factory.getBeanDefinitionNames()) {
                BeanDefinition bd = factory.getBeanDefinition(name);
                String className = resolveBeanClassName(bd);
                if (className == null) {
                    continue;
                }
                try {
                    Class<?> original = Class.forName(className);
                    for (Method method : original.getMethods()) {
                        if (!method.isAnnotationPresent(AfterProxy.class)) {
                            continue;
                        }
                        Object bean = event.getApplicationContext().getBean(name);
                        Method onProxy = bean.getClass()
                                .getMethod(method.getName(), method.getParameterTypes());
                        onProxy.invoke(bean);
                    }
                } catch (ClassNotFoundException ignored) {
                    // служебные BD без реального класса
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("AfterProxy failed for " + name, e);
                }
            }
        }

        /**
         * У {@code @Bean} часто {@code beanDefinition.getBeanClassName() == null}:
         * Spring не знает return type до вызова factory-метода. Берём из MethodMetadata.
         */
        private static String resolveBeanClassName(BeanDefinition bd) {
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

        /** Инфраструктура — static @Bean, иначе цикл с ранней инициализацией @Configuration. */
        @Bean
        static BeanPostProcessor injectRandomIntBpp() {
            System.out.println("[infra] static @Bean InjectRandomIntBpp");
            return new InjectRandomIntBpp();
        }

        @Bean
        static BeanPostProcessor profilingBpp() {
            System.out.println("[infra] static @Bean ProfilingBpp");
            return new ProfilingBpp();
        }

        @Bean
        AfterProxyInvoker afterProxyInvoker() {
            System.out.println("[infra] @Bean AfterProxyInvoker (ApplicationListener)");
            return new AfterProxyInvoker();
        }

        /**
         * Return type именно реализации: у {@code @Bean} в BeanDefinition иначе
         * {@code MethodMetadata.getReturnTypeName()} = интерфейс, и {@code @AfterProxy}
         * на классе-реализации listener не увидит.
         */
        @Bean
        TerminatorQuoter terminatorQuoter() {
            System.out.println("[factory] @Bean terminatorQuoter → new TerminatorQuoter");
            return new TerminatorQuoter("I'll be back");
        }
    }
}
