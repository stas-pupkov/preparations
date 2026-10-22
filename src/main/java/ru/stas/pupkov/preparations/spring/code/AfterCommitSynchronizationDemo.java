package ru.stas.pupkov.preparations.spring.code;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code TransactionSynchronization.afterCommit}: колбэк строго после успешного commit.
 * <p>
 * Порядок на commit (упрощённо, {@code AbstractPlatformTransactionManager}):
 * {@code beforeCommit} → {@code beforeCompletion} → {@code doCommit} →
 * {@code afterCommit} → {@code afterCompletion(STATUS_COMMITTED)}.
 * На rollback: {@code beforeCompletion} → {@code doRollback} →
 * {@code afterCompletion(STATUS_ROLLED_BACK)} — {@code afterCommit} не вызывается.
 * <p>
 * Важно: внутри {@code afterCommit} ресурсы tx ещё могут быть «живы» (Connection/Session
 * bound к потоку). Любой новый DB-доступ «участвует» в уже закоммиченной tx без повторного
 * commit — для реальной работы с БД нужен {@code PROPAGATION_REQUIRES_NEW}.
 * <p>
 * Запуск (после compileJava), с runtime classpath Gradle.
 */
public final class AfterCommitSynchronizationDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        OrderService orders = ctx.getBean(OrderService.class);

        System.out.println("=== 1. успешный commit → afterCommit + afterCompletion(COMMITTED) ===");
        orders.placeOrder("ORD-1", false);

        System.out.println();
        System.out.println("=== 2. rollback → afterCommit НЕ вызывается, только afterCompletion(ROLLED_BACK) ===");
        try {
            orders.placeOrder("ORD-2", true);
        } catch (RuntimeException ex) {
            System.out.println("  caller saw: " + ex.getMessage());
        }

        System.out.println();
        System.out.println("=== 3. Ordered: меньший order — раньше в цепочке sync ===");
        orders.placeOrderWithOrderedSyncs("ORD-3");

        ctx.close();
    }

    static class OrderService {

        @Transactional
        public void placeOrder(String id, boolean fail) {
            System.out.printf("  [placeOrder] id=%s txActive=%s syncActive=%s%n",
                    id,
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isSynchronizationActive());

            // Регистрация только при активной synchronization (есть outer tx от TM)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    System.out.println("  [sync] beforeCommit readOnly=" + readOnly);
                }

                @Override
                public void beforeCompletion() {
                    System.out.println("  [sync] beforeCompletion");
                }

                @Override
                public void afterCommit() {
                    // Здесь безопасно: fire event / @Async / publish to MQ.
                    // НЕ делать «просто Repository.save» без REQUIRES_NEW — см. javadoc Spring.
                    System.out.printf("  [sync] afterCommit → notifyAsync for %s "
                                    + "(txActive still=%s — resource может быть bound)%n",
                            id,
                            TransactionSynchronizationManager.isActualTransactionActive());
                }

                @Override
                public void afterCompletion(int status) {
                    String label = switch (status) {
                        case STATUS_COMMITTED -> "COMMITTED";
                        case STATUS_ROLLED_BACK -> "ROLLED_BACK";
                        default -> "UNKNOWN(" + status + ")";
                    };
                    System.out.println("  [sync] afterCompletion status=" + label);
                }
            });

            System.out.println("  [placeOrder] business work for " + id);
            if (fail) {
                throw new IllegalStateException("business failure → rollback");
            }
        }

        @Transactional
        public void placeOrderWithOrderedSyncs(String id) {
            TransactionSynchronizationManager.registerSynchronization(ordered("late", Ordered.LOWEST_PRECEDENCE));
            TransactionSynchronizationManager.registerSynchronization(ordered("early", Ordered.HIGHEST_PRECEDENCE));
            System.out.println("  [placeOrderWithOrderedSyncs] work " + id);
        }

        private static TransactionSynchronization ordered(String name, int order) {
            return new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return order;
                }

                @Override
                public void afterCommit() {
                    System.out.println("  [sync/" + name + "] afterCommit order=" + order);
                }
            };
        }
    }

    static final class LoggingTxManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            System.out.println("  [TM] BEGIN name=" + definition.getName());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            System.out.println("  [TM] COMMIT");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            System.out.println("  [TM] ROLLBACK");
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new LoggingTxManager();
        }

        @Bean
        OrderService orderService() {
            return new OrderService();
        }
    }
}
