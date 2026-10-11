package ru.stas.pupkov.preparations.java8.code;

/**
 * Конфликт default-методов и выбор через {@code Interface.super.method()}.
 */
public class DefaultMethodConflictDemo {

    interface Alpha {
        default String tag() {
            return "Alpha";
        }
    }

    interface Beta {
        default String tag() {
            return "Beta";
        }
    }

    static final class Combined implements Alpha, Beta {
        @Override
        public String tag() {
            return Alpha.super.tag() + "+" + Beta.super.tag();
        }
    }

    static final class PrefersClass extends Base implements Alpha {
        // метод класса Base побеждает default из Alpha без override
    }

    static class Base {
        public String tag() {
            return "Base";
        }
    }

    public static void main(String[] args) {
        System.out.println("conflict resolved: " + new Combined().tag());
        System.out.println("class wins over default: " + new PrefersClass().tag());
        System.out.println("static on interface: " + Util.version());
    }

    interface Util {
        static String version() {
            return "interface-static-1";
        }
    }
}
