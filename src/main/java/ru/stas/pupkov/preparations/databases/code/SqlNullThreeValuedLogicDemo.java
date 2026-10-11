package ru.stas.pupkov.preparations.databases.code;

/**
 * Трёхзначная логика SQL: сравнения с NULL дают UNKNOWN, не FALSE.
 * {@code NULL = NULL} → UNKNOWN; фильтр WHERE оставляет только TRUE.
 */
public class SqlNullThreeValuedLogicDemo {

    enum Tri { TRUE, FALSE, UNKNOWN }

    public static void main(String[] args) {
        Integer a = null;
        Integer b = null;
        Integer c = 5;

        System.out.println("NULL = NULL      => " + eq(a, b));
        System.out.println("NULL = 5         => " + eq(a, c));
        System.out.println("5 = 5            => " + eq(c, 5));
        System.out.println("NULL IS NULL     => " + (a == null ? Tri.TRUE : Tri.FALSE));
        System.out.println("WHERE keeps only TRUE rows:");
        for (Tri t : new Tri[]{Tri.TRUE, Tri.FALSE, Tri.UNKNOWN}) {
            System.out.println("  " + t + " -> " + (t == Tri.TRUE ? "pass" : "filtered out"));
        }
        System.out.println("COALESCE(NULL, 0) => " + coalesce(a, 0));
    }

    private static Tri eq(Integer left, Integer right) {
        if (left == null || right == null) {
            return Tri.UNKNOWN;
        }
        return left.equals(right) ? Tri.TRUE : Tri.FALSE;
    }

    private static int coalesce(Integer value, int fallback) {
        return value != null ? value : fallback;
    }
}
