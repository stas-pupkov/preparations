package ru.stas.pupkov.preparations.databases.code;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Состояния JPA entity и роль persistence context (упрощённая in-memory модель EntityManager).
 */
public class EntityLifecycleDemo {

    enum State { TRANSIENT, MANAGED, DETACHED, REMOVED }

    static final class Product {
        Long id;
        String name;
        State state = State.TRANSIENT;

        Product(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Product{id=" + id + ", name='" + name + "', state=" + state + "}";
        }
    }

    /** Минимальный persistence context: identity map + pending SQL. */
    static final class FakeEntityManager {
        private final AtomicLong seq = new AtomicLong(1);
        private final Map<Long, Product> identityMap = new LinkedHashMap<>();
        private final Map<Product, Product> managedByIdentity = new IdentityHashMap<>();
        private boolean open = true;

        void persist(Product p) {
            requireOpen();
            if (p.state == State.MANAGED) {
                return;
            }
            if (p.id == null) {
                p.id = seq.getAndIncrement();
            }
            p.state = State.MANAGED;
            identityMap.put(p.id, p);
            managedByIdentity.put(p, p);
            System.out.println("persist -> schedule INSERT id=" + p.id + " (SQL may wait until flush)");
        }

        Product find(Long id) {
            requireOpen();
            Product cached = identityMap.get(id);
            if (cached != null) {
                System.out.println("find(" + id + ") -> same instance from PC (no SQL)");
                return cached;
            }
            System.out.println("find(" + id + ") -> SELECT + put into PC");
            Product loaded = new Product("loaded-" + id);
            loaded.id = id;
            loaded.state = State.MANAGED;
            identityMap.put(id, loaded);
            managedByIdentity.put(loaded, loaded);
            return loaded;
        }

        void flush() {
            requireOpen();
            System.out.println("flush -> synchronize PC to DB (INSERT/UPDATE/DELETE)");
            for (Product p : managedByIdentity.keySet()) {
                if (p.state == State.REMOVED) {
                    identityMap.remove(p.id);
                }
            }
            managedByIdentity.entrySet().removeIf(e -> e.getKey().state == State.REMOVED);
        }

        void clear() {
            for (Product p : managedByIdentity.keySet()) {
                if (p.state == State.MANAGED) {
                    p.state = State.DETACHED;
                }
            }
            identityMap.clear();
            managedByIdentity.clear();
            System.out.println("clear -> all managed become DETACHED; PC empty");
        }

        void remove(Product p) {
            requireOpen();
            Objects.requireNonNull(p.id, "remove needs id");
            p.state = State.REMOVED;
            System.out.println("remove -> schedule DELETE id=" + p.id);
        }

        void close() {
            clear();
            open = false;
            System.out.println("close EM");
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException("EntityManager closed");
            }
        }
    }

    public static void main(String[] args) {
        FakeEntityManager em = new FakeEntityManager();

        Product p = new Product("keyboard");
        System.out.println("1) new entity: " + p);

        em.persist(p);
        System.out.println("2) after persist: " + p);

        p.name = "mech-keyboard";
        System.out.println("3) field changed on MANAGED -> dirty; UPDATE on flush (no explicit save)");
        em.flush();

        Product same = em.find(p.id);
        System.out.println("4) identity map: p == find(id) ? " + (p == same));

        em.remove(p);
        em.flush();
        System.out.println("5) after remove+flush: " + p);

        Product again = new Product("mouse");
        em.persist(again);
        em.clear();
        System.out.println("6) after clear: " + again);
        System.out.println("   detached changes are NOT written unless merge/reattach in new PC");
        again.name = "gaming-mouse";
        System.out.println("   changed detached: " + again);

        em.close();
    }
}
