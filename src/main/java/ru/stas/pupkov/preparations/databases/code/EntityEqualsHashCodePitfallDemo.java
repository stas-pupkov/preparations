package ru.stas.pupkov.preparations.databases.code;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Почему equals/hashCode по generated id ломает Set до flush и после смены id.
 */
public class EntityEqualsHashCodePitfallDemo {

    /** Антипаттерн: hash от nullable generated id. */
    static final class BadEntity {
        Long id;
        String sku;

        BadEntity(String sku) {
            this.sku = sku;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BadEntity other)) {
                return false;
            }
            return id != null && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id == null ? 0 : id.hashCode();
        }

        @Override
        public String toString() {
            return "BadEntity{id=" + id + ", sku='" + sku + "'}";
        }
    }

    /** Более устойчивый вариант: business key. */
    static final class BetterEntity {
        Long id;
        final String sku;

        BetterEntity(String sku) {
            this.sku = Objects.requireNonNull(sku);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BetterEntity other)) {
                return false;
            }
            return sku.equals(other.sku);
        }

        @Override
        public int hashCode() {
            return sku.hashCode();
        }

        @Override
        public String toString() {
            return "BetterEntity{id=" + id + ", sku='" + sku + "'}";
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Bad: equals by id ===");
        Set<BadEntity> bad = new HashSet<>();
        BadEntity a = new BadEntity("SKU-1");
        BadEntity b = new BadEntity("SKU-2");
        bad.add(a);
        bad.add(b);
        System.out.println("before persist, set size=" + bad.size()
                + " (id=null => equals is false, but hashCode=0 for all -> one bucket)");
        System.out.println("contains a? " + bad.contains(a));

        a.id = 1L;
        b.id = 2L;
        System.out.println("after assigning ids, contains a? " + bad.contains(a)
                + " (hashCode changed after insert into HashSet -> lookup misses)");
        System.out.println("set size still reported as " + bad.size() + ", iteration:");
        bad.forEach(e -> System.out.println("  " + e));

        System.out.println();
        System.out.println("=== Better: business key (sku) ===");
        Set<BetterEntity> ok = new HashSet<>();
        BetterEntity x = new BetterEntity("SKU-1");
        BetterEntity y = new BetterEntity("SKU-2");
        ok.add(x);
        ok.add(y);
        x.id = 10L;
        y.id = 20L;
        System.out.println("after ids, contains x? " + ok.contains(x) + ", size=" + ok.size());
        ok.forEach(e -> System.out.println("  " + e));
    }
}
