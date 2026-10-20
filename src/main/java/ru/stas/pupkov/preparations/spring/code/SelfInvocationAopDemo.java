package ru.stas.pupkov.preparations.spring.code;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Self-invocation обходит Spring AOP proxy: this.audited() не попадает в advice.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class SelfInvocationAopDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        OrderFacade facade = ctx.getBean(OrderFacade.class);

        System.out.println("=== external call through proxy ===");
        facade.audited("external");

        System.out.println();
        System.out.println("=== internal self-invocation (this.audited) ===");
        facade.entryViaSelf("self");

        System.out.println();
        System.out.println("proxy class = " + facade.getClass().getName());
        ctx.close();
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class Config {

        @Bean
        OrderFacade orderFacade() {
            return new OrderFacade();
        }

        @Bean
        AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    @Aspect
    static class AuditAspect {

        @Around("execution(* ru.stas.pupkov.preparations.spring.code.SelfInvocationAopDemo.OrderFacade.audited(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [ASPECT] before " + pjp.getSignature().getName());
            Object result = pjp.proceed();
            System.out.println("  [ASPECT] after");
            return result;
        }
    }

    static class OrderFacade {

        public void entryViaSelf(String id) {
            System.out.println("entryViaSelf → вызывает this.audited");
            audited(id);
        }

        public void audited(String id) {
            System.out.println("  audited body, id=" + id);
        }
    }
}
