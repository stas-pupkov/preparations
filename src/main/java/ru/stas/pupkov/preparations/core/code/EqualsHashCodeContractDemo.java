package ru.stas.pupkov.preparations.core.code;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Контракт {@code equals}/{@code hashCode}: overload вместо override, equals без hashCode,
 * {@code getClass()} vs {@code instanceof}.
 */
public class EqualsHashCodeContractDemo {

    /** Перегрузка equals(Point) — коллекциям невидима, остаётся identity-equals. */
    static final class BrokenOverloadEquals {
        final int x;
        final int y;

        BrokenOverloadEquals(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public boolean equals(BrokenOverloadEquals that) {
            return that != null && x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    /** equals есть, hashCode по умолчанию (identity) — HashSet ломается. */
    static final class EqualsWithoutHashCode {
        final int x;
        final int y;

        EqualsWithoutHashCode(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof EqualsWithoutHashCode that)) {
                return false;
            }
            return x == that.x && y == that.y;
        }
    }

    /** getClass(): наследник никогда не равен родителю. */
    static class PointGetClass {
        final int x;
        final int y;

        PointGetClass(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            PointGetClass that = (PointGetClass) obj;
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPointGetClass extends PointGetClass {
        final String color;

        ColoredPointGetClass(int x, int y, String color) {
            super(x, y);
            this.color = color;
        }
    }

    /** instanceof: симметрия ломается, если subclass добавляет поля в equals. */
    static class PointInstanceOf {
        final int x;
        final int y;

        PointInstanceOf(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PointInstanceOf that)) {
                return false;
            }
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPointInstanceOf extends PointInstanceOf {
        final String color;

        ColoredPointInstanceOf(int x, int y, String color) {
            super(x, y);
            this.color = color;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ColoredPointInstanceOf that)) {
                return false;
            }
            return super.equals(that) && Objects.equals(color, that.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    public static void main(String[] args) {
        System.out.println("--- overload equals(Point) is NOT Object.equals ---");
        BrokenOverloadEquals a = new BrokenOverloadEquals(1, 2);
        BrokenOverloadEquals b = new BrokenOverloadEquals(1, 2);
        System.out.println("a.equals(b) typed: " + a.equals(b));
        System.out.println("a.equals((Object) b): " + a.equals((Object) b));
        Set<BrokenOverloadEquals> overloadSet = new HashSet<>();
        overloadSet.add(a);
        System.out.println("HashSet.contains(b): " + overloadSet.contains(b));

        System.out.println("--- equals without hashCode ---");
        EqualsWithoutHashCode p1 = new EqualsWithoutHashCode(3, 4);
        EqualsWithoutHashCode p2 = new EqualsWithoutHashCode(3, 4);
        System.out.println("p1.equals(p2): " + p1.equals(p2));
        System.out.println("hashCodes equal?: " + (p1.hashCode() == p2.hashCode()));
        Set<EqualsWithoutHashCode> brokenSet = new HashSet<>();
        brokenSet.add(p1);
        System.out.println("HashSet.contains equal value: " + brokenSet.contains(p2));

        System.out.println("--- getClass vs instanceof ---");
        PointGetClass baseGc = new PointGetClass(1, 1);
        ColoredPointGetClass coloredGc = new ColoredPointGetClass(1, 1, "red");
        System.out.println("getClass equals: " + baseGc.equals(coloredGc));

        PointInstanceOf baseIo = new PointInstanceOf(1, 1);
        ColoredPointInstanceOf coloredIo = new ColoredPointInstanceOf(1, 1, "red");
        System.out.println("base.equals(colored): " + baseIo.equals(coloredIo));
        System.out.println("colored.equals(base): " + coloredIo.equals(baseIo));
        System.out.println("symmetry broken: "
                + (baseIo.equals(coloredIo) != coloredIo.equals(baseIo)));
    }
}
