package ru.stas.pupkov.preparations.core.code;

/**
 * Autoboxing: identity кэша {@code Integer} (−128…127 по умолчанию), {@code ==} на обёртках,
 * NPE при unboxing {@code null}.
 *
 * <p>Диапазон {@code Integer} расширяется {@code -XX:AutoBoxCacheMax}; у {@code Long} тот же
 * дефолт, у {@code Boolean}/{@code Byte}/{@code Character}/{@code Short} — свои правила.
 */
public class AutoboxingCacheDemo {

    public static void main(String[] args) {
        System.out.println("--- Integer cache default range ---");
        Integer a = 127;
        Integer b = 127;
        Integer c = 128;
        Integer d = 128;
        System.out.println("127 == 127 (identity): " + (a == b));
        System.out.println("128 == 128 (identity): " + (c == d));
        System.out.println("128 equals 128: " + c.equals(d));

        System.out.println("--- valueOf: cache hit vs miss ---");
        Integer cached = Integer.valueOf(42);
        Integer alsoCached = Integer.valueOf(42);
        Integer outsideCacheA = Integer.valueOf(10_000);
        Integer outsideCacheB = Integer.valueOf(10_000);
        System.out.println("valueOf(42) identity: " + (cached == alsoCached));
        System.out.println("valueOf(10000) identity: " + (outsideCacheA == outsideCacheB));

        System.out.println("--- Boolean / Character caches ---");
        System.out.println("Boolean TRUE identity: " + (Boolean.valueOf(true) == Boolean.TRUE));
        Character ch1 = Character.valueOf('A');
        Character ch2 = Character.valueOf('A');
        System.out.println("Character 'A' identity: " + (ch1 == ch2));

        System.out.println("--- unboxing null -> NPE ---");
        Integer maybe = null;
        try {
            int exploded = maybe;
            System.out.println(exploded);
        } catch (NullPointerException npe) {
            System.out.println("caught: " + npe.getClass().getSimpleName());
        }

        System.out.println("--- compound assignment boxes back ---");
        Integer counter = 10;
        counter++; // unbox, increment, box
        System.out.println("counter after ++: " + counter);
    }
}
