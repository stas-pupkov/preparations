package ru.stas.pupkov.preparations.spring.code;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * {@code @Async} + {@code @Transactional}: tx caller'а не продолжается; надёжный паттерн —
 * {@code @Async} фасад вызывает другой бин с {@code @Transactional} на worker-потоке.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class AsyncTransactionalDemo {

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        AsyncFacade facade = ctx.getBean(AsyncFacade.class);
        WorkService work = ctx.getBean(WorkService.class);

        System.out.println("=== 1. caller thread (нет tx) → @Async → другой бин @Transactional ===");
        System.out.println("  caller thread=" + Thread.currentThread().getName()
                + " txActive=" + TransactionSynchronizationManager.isActualTransactionActive());
        facade.processAsync("ok-path");
        work.await(3);

        System.out.println();
        System.out.println("=== 2. self-invocation внутри @Async: @Transactional молчит ===");
        facade.processAsyncBrokenSelf("broken");
        work.await(3);

        ctx.close();
    }

    static class WorkService {

        private volatile CountDownLatch latch = new CountDownLatch(1);

        void resetLatch() {
            latch = new CountDownLatch(1);
        }

        void await(long sec) throws InterruptedException {
            if (!latch.await(sec, TimeUnit.SECONDS)) {
                System.out.println("  (timeout waiting async)");
            }
            resetLatch();
        }

        @Transactional
        public void doInTx(String label) {
            System.out.printf("  [WorkService.doInTx] thread=%s txActive=%s label=%s%n",
                    Thread.currentThread().getName(),
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    label);
            done();
        }

        void done() {
            latch.countDown();
        }
    }

    static class AsyncFacade {

        @Autowired
        private WorkService work;

        @Async
        public void processAsync(String label) {
            System.out.printf("  [AsyncFacade] worker thread=%s txActive=%s → call WorkService%n",
                    Thread.currentThread().getName(),
                    TransactionSynchronizationManager.isActualTransactionActive());
            work.doInTx(label); // другой бин → proxy → tx на этом же worker-потоке
        }

        @Async
        public void processAsyncBrokenSelf(String label) {
            System.out.printf("  [AsyncFacade broken] worker thread=%s → this.doInTx%n",
                    Thread.currentThread().getName());
            doInTxLocal(label); // self-invocation → tx не откроется
        }

        @Transactional
        public void doInTxLocal(String label) {
            System.out.printf("  [doInTxLocal] thread=%s txActive=%s label=%s%n",
                    Thread.currentThread().getName(),
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    label);
            work.done();
        }
    }

    static final class LoggingTxManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            System.out.println("  [TM] BEGIN on " + Thread.currentThread().getName()
                    + " name=" + definition.getName());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            System.out.println("  [TM] COMMIT on " + Thread.currentThread().getName());
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            System.out.println("  [TM] ROLLBACK on " + Thread.currentThread().getName());
        }
    }

    @Configuration
    @EnableAsync
    @EnableTransactionManagement
    static class Config implements AsyncConfigurer {

        @Override
        public Executor getAsyncExecutor() {
            SimpleAsyncTaskExecutor exec = new SimpleAsyncTaskExecutor("async-");
            exec.setDaemon(true);
            return exec;
        }

        @Override
        public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
            return (Throwable ex, Method method, Object... params) ->
                    System.out.println("  async error in " + method.getName() + ": " + ex);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new LoggingTxManager();
        }

        @Bean
        WorkService workService() {
            return new WorkService();
        }

        @Bean
        AsyncFacade asyncFacade() {
            return new AsyncFacade();
        }
    }
}
