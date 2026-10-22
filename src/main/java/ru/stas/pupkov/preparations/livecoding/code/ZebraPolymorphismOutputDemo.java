package ru.stas.pupkov.preparations.livecoding.code;

/**
 * Поле и static — по типу ссылки; instance-метод — полиморфно.
 * <pre>
 * furry bray foo
 * stripes bray baz
 * </pre>
 */
public class ZebraPolymorphismOutputDemo {

    public static void main(String[] args) {
        new ZooKeeper().go();
    }

    static class Mammal {
        String name = "furry ";

        String makeNoise() {
            return "generic noise ";
        }

        static String foo() {
            return "foo ";
        }
    }

    static class Zebra extends Mammal {
        String name = "stripes ";

        @Override
        String makeNoise() {
            return "bray ";
        }

        static String foo() {
            return "baz ";
        }
    }

    static class ZooKeeper {
        void go() {
            Mammal m = new Zebra();
            System.out.println(m.name + m.makeNoise() + m.foo());

            Zebra z = new Zebra();
            System.out.println(z.name + z.makeNoise() + z.foo());
        }
    }
}
