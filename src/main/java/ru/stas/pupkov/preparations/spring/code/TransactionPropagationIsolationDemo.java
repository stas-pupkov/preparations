package ru.stas.pupkov.preparations.spring.code;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.SavepointManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionUsageException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Практические сценарии {@code propagation} и {@code isolation} у {@code @Transactional}.
 * TM — логирующий stub: видно begin/suspend/resume/commit и запрошенный isolation.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class TransactionPropagationIsolationDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        Scenarios scenarios = ctx.getBean(Scenarios.class);

        System.out.println("\n########## PROPAGATION ##########\n");
        scenarios.requiredJoinsOuter();
        scenarios.requiresNewIndependent();
        scenarios.nestedSavepointStyle();
        scenarios.supportsWithoutOuter();
        scenarios.supportsWithOuter();
        scenarios.notSupportedSuspends();
        scenarios.mandatoryFailsWithoutTx();
        scenarios.mandatoryOkWithOuter();
        scenarios.neverFailsWithOuter();

        System.out.println("\n########## ISOLATION ##########\n");
        scenarios.readCommittedDefaultStyle();
        scenarios.repeatableReadReport();
        scenarios.serializableCritical();

        System.out.println("\n########## КОМБИНАЦИИ ##########\n");
        scenarios.checkoutPlusAudit();
        scenarios.readOnlyReportInsideWrite();

        ctx.close();
    }

    static void trace(String label) {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        System.out.printf("  [%s] active=%s name=%s isolation=%s%n",
                label, active, name, isolationName(isolation));
    }

    static String isolationName(Integer level) {
        if (level == null) {
            return "null";
        }
        return switch (level) {
            case TransactionDefinition.ISOLATION_READ_UNCOMMITTED -> "READ_UNCOMMITTED";
            case TransactionDefinition.ISOLATION_READ_COMMITTED -> "READ_COMMITTED";
            case TransactionDefinition.ISOLATION_REPEATABLE_READ -> "REPEATABLE_READ";
            case TransactionDefinition.ISOLATION_SERIALIZABLE -> "SERIALIZABLE";
            case TransactionDefinition.ISOLATION_DEFAULT -> "DEFAULT";
            default -> String.valueOf(level);
        };
    }

    static String propagationName(int p) {
        return switch (p) {
            case TransactionDefinition.PROPAGATION_REQUIRED -> "REQUIRED";
            case TransactionDefinition.PROPAGATION_REQUIRES_NEW -> "REQUIRES_NEW";
            case TransactionDefinition.PROPAGATION_NESTED -> "NESTED";
            case TransactionDefinition.PROPAGATION_SUPPORTS -> "SUPPORTS";
            case TransactionDefinition.PROPAGATION_NOT_SUPPORTED -> "NOT_SUPPORTED";
            case TransactionDefinition.PROPAGATION_MANDATORY -> "MANDATORY";
            case TransactionDefinition.PROPAGATION_NEVER -> "NEVER";
            default -> String.valueOf(p);
        };
    }

    /** Логирует lifecycle tx; NESTED — через in-memory savepoints. */
    static final class LoggingTxManager extends AbstractPlatformTransactionManager {

        LoggingTxManager() {
            setNestedTransactionAllowed(true);
        }

        @Override
        protected Object doGetTransaction() {
            DemoTransaction tx = new DemoTransaction();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                tx.existing = true;
            }
            return tx;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((DemoTransaction) transaction).existing;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            DemoTransaction tx = (DemoTransaction) transaction;
            tx.existing = true;
            System.out.printf("  [TM] BEGIN propagation=%s isolation=%s name=%s%n",
                    propagationName(definition.getPropagationBehavior()),
                    isolationName(definition.getIsolationLevel()),
                    definition.getName());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            System.out.println("  [TM] COMMIT");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            System.out.println("  [TM] ROLLBACK");
        }

        @Override
        protected Object doSuspend(Object transaction) {
            System.out.println("  [TM] SUSPEND outer");
            return transaction;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            System.out.println("  [TM] RESUME outer");
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            System.out.println("  [TM] setRollbackOnly");
            super.doSetRollbackOnly(status);
        }
    }

    static final class DemoTransaction implements SavepointManager {
        boolean existing;
        private int savepointSeq;
        private final Deque<String> savepoints = new ArrayDeque<>();

        @Override
        public Object createSavepoint() throws TransactionUsageException {
            String sp = "SP-" + (++savepointSeq);
            savepoints.push(sp);
            System.out.println("  [TM] SAVEPOINT " + sp);
            return sp;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
            System.out.println("  [TM] ROLLBACK TO " + savepoint);
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
            System.out.println("  [TM] RELEASE " + savepoint);
        }
    }

    static class InnerService {

        @Transactional(propagation = Propagation.REQUIRED)
        public void requiredWork() {
            trace("INNER REQUIRED");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void requiresNewWork() {
            trace("INNER REQUIRES_NEW");
        }

        @Transactional(propagation = Propagation.NESTED)
        public void nestedWork() {
            trace("INNER NESTED");
        }

        @Transactional(propagation = Propagation.SUPPORTS)
        public void supportsWork() {
            trace("INNER SUPPORTS");
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        public void notSupportedWork() {
            trace("INNER NOT_SUPPORTED");
        }

        @Transactional(propagation = Propagation.MANDATORY)
        public void mandatoryWork() {
            trace("INNER MANDATORY");
        }

        @Transactional(propagation = Propagation.NEVER)
        public void neverWork() {
            trace("INNER NEVER");
        }

        /** Аудит: отдельная tx + жёсткая isolation. */
        @Transactional(
                propagation = Propagation.REQUIRES_NEW,
                isolation = Isolation.SERIALIZABLE)
        public void auditLog(String event) {
            trace("AUDIT REQUIRES_NEW+SERIALIZABLE event=" + event);
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
        public void heavyReadOutsideTx() {
            trace("HEAVY_READ NOT_SUPPORTED (без hold connection надолго в outer write)");
        }
    }

    static class OuterService {

        @Autowired
        private InnerService inner;

        @Transactional(propagation = Propagation.REQUIRED)
        public void withRequiredInner() {
            trace("OUTER REQUIRED (start)");
            inner.requiredWork();
            trace("OUTER REQUIRED (end)");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void withRequiresNewInner() {
            trace("OUTER REQUIRED (start)");
            inner.requiresNewWork();
            trace("OUTER REQUIRED (end)");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void withNestedInner() {
            trace("OUTER REQUIRED (start)");
            inner.nestedWork();
            trace("OUTER REQUIRED (end)");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void withSupportsInner() {
            trace("OUTER REQUIRED (start)");
            inner.supportsWork();
            trace("OUTER REQUIRED (end)");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void withNotSupportedInner() {
            trace("OUTER REQUIRED (start)");
            inner.notSupportedWork();
            trace("OUTER REQUIRED (end)");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void withMandatoryInner() {
            trace("OUTER REQUIRED (start)");
            inner.mandatoryWork();
            trace("OUTER REQUIRED (end)");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void withNeverInner() {
            trace("OUTER REQUIRED (start)");
            inner.neverWork();
            trace("OUTER REQUIRED (end)");
        }

        /** Checkout: write REQUIRED + READ_COMMITTED, audit отдельно. */
        @Transactional(
                propagation = Propagation.REQUIRED,
                isolation = Isolation.READ_COMMITTED)
        public void checkout() {
            trace("CHECKOUT REQUIRED+READ_COMMITTED");
            inner.auditLog("ORDER_PAID");
            trace("CHECKOUT after audit");
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void writeThenHeavyRead() {
            trace("WRITE REQUIRED");
            inner.heavyReadOutsideTx();
            trace("WRITE after heavy read");
        }
    }

    static class ReportService {

        @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
        public void reportReadCommitted() {
            trace("REPORT READ_COMMITTED readOnly");
        }

        @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
        public void reportRepeatableRead() {
            trace("REPORT REPEATABLE_READ readOnly");
        }

        @Transactional(isolation = Isolation.SERIALIZABLE)
        public void criticalSerializable() {
            trace("CRITICAL SERIALIZABLE");
        }
    }

    static class Scenarios {

        @Autowired
        private OuterService outer;
        @Autowired
        private InnerService inner;
        @Autowired
        private ReportService reports;

        void requiredJoinsOuter() {
            System.out.println("--- REQUIRED внутри REQUIRED: одна tx ---");
            outer.withRequiredInner();
            System.out.println();
        }

        void requiresNewIndependent() {
            System.out.println("--- REQUIRES_NEW: suspend outer, своя tx, resume ---");
            outer.withRequiresNewInner();
            System.out.println();
        }

        void nestedSavepointStyle() {
            System.out.println("--- NESTED: та же физическая tx + savepoint ---");
            outer.withNestedInner();
            System.out.println();
        }

        void supportsWithoutOuter() {
            System.out.println("--- SUPPORTS без outer: работа без tx ---");
            inner.supportsWork();
            System.out.println();
        }

        void supportsWithOuter() {
            System.out.println("--- SUPPORTS с outer: присоединяется ---");
            outer.withSupportsInner();
            System.out.println();
        }

        void notSupportedSuspends() {
            System.out.println("--- NOT_SUPPORTED: suspend, работа вне tx ---");
            outer.withNotSupportedInner();
            System.out.println();
        }

        void mandatoryFailsWithoutTx() {
            System.out.println("--- MANDATORY без outer → IllegalTransactionStateException ---");
            try {
                inner.mandatoryWork();
            } catch (IllegalTransactionStateException e) {
                System.out.println("  caught: " + e.getClass().getSimpleName());
            }
            System.out.println();
        }

        void mandatoryOkWithOuter() {
            System.out.println("--- MANDATORY с outer: ок ---");
            outer.withMandatoryInner();
            System.out.println();
        }

        void neverFailsWithOuter() {
            System.out.println("--- NEVER с outer → IllegalTransactionStateException ---");
            try {
                outer.withNeverInner();
            } catch (IllegalTransactionStateException e) {
                System.out.println("  caught: " + e.getClass().getSimpleName());
            }
            System.out.println();
        }

        void readCommittedDefaultStyle() {
            System.out.println("--- isolation READ_COMMITTED (типичный read API) ---");
            reports.reportReadCommitted();
            System.out.println();
        }

        void repeatableReadReport() {
            System.out.println("--- isolation REPEATABLE_READ (стабильное чтение в рамках tx) ---");
            reports.reportRepeatableRead();
            System.out.println();
        }

        void serializableCritical() {
            System.out.println("--- isolation SERIALIZABLE (критичный участок) ---");
            reports.criticalSerializable();
            System.out.println();
        }

        void checkoutPlusAudit() {
            System.out.println("--- combo: checkout REQUIRED+RC + audit REQUIRES_NEW+SERIALIZABLE ---");
            outer.checkout();
            System.out.println();
        }

        void readOnlyReportInsideWrite() {
            System.out.println("--- combo: write REQUIRED + heavy read NOT_SUPPORTED ---");
            outer.writeThenHeavyRead();
            System.out.println();
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
        InnerService innerService() {
            return new InnerService();
        }

        @Bean
        OuterService outerService() {
            return new OuterService();
        }

        @Bean
        ReportService reportService() {
            return new ReportService();
        }

        @Bean
        Scenarios scenarios() {
            return new Scenarios();
        }
    }
}
