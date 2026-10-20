package ru.stas.pupkov.preparations.spring.code;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @Transactional через proxy: прямой this-вызов без tx; self + @Lazy — с tx.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class TransactionalSelfInvocationDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        PaymentService service = ctx.getBean(PaymentService.class);

        System.out.println("=== 1. self-invocation (this.pay) ===");
        service.entryDirect();

        System.out.println();
        System.out.println("=== 2. через self-proxy (@Lazy) ===");
        service.entryViaSelf();

        System.out.println();
        System.out.println("proxy class = " + service.getClass().getName());
        ctx.close();
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new LoggingTxManager();
        }

        @Bean
        PaymentService paymentService() {
            return new PaymentService();
        }
    }

    static final class LoggingTxManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            System.out.println("  [TX] begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            System.out.println("  [TX] commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            System.out.println("  [TX] rollback");
        }
    }

    static class PaymentService {

        @Autowired
        @Lazy
        private PaymentService self;

        void entryDirect() {
            pay("direct");
        }

        void entryViaSelf() {
            self.pay("via-self");
        }

        @Transactional
        public void pay(String label) {
            boolean active = TransactionSynchronizationManager.isActualTransactionActive();
            System.out.println("  pay(" + label + ") txActive=" + active);
        }
    }
}
