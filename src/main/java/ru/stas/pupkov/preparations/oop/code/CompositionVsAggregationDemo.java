package ru.stas.pupkov.preparations.oop.code;

/**
 * Критерий — владение жизненным циклом, а не «new внутри / снаружи».
 */
public class CompositionVsAggregationDemo {

    static final class Engine {
        private final int horsepower;

        Engine(int horsepower) {
            this.horsepower = horsepower;
        }

        int horsepower() {
            return horsepower;
        }
    }

    static final class Driver {
        private final String name;

        Driver(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }

    /** Композиция: Car владеет Engine; снаружи Engine не переиспользуется. */
    static final class CarWithOwnedEngine {
        private final Engine engine;

        CarWithOwnedEngine(int horsepower) {
            this.engine = new Engine(horsepower);
        }

        /** Тот же смысл композиции: Engine передан, но владение у Car. */
        static CarWithOwnedEngine withInjectedEngine(Engine engine) {
            return new CarWithOwnedEngine(engine);
        }

        private CarWithOwnedEngine(Engine engine) {
            this.engine = engine;
        }

        Engine engine() {
            return engine;
        }
    }

    /** Агрегация: Driver живёт независимо от Car. */
    static final class CarWithDriver {
        private Driver driver;

        CarWithDriver(Driver driver) {
            this.driver = driver;
        }

        void assign(Driver driver) {
            this.driver = driver;
        }

        Driver driver() {
            return driver;
        }
    }

    public static void main(String[] args) {
        CarWithOwnedEngine composed = new CarWithOwnedEngine(150);
        System.out.println("composition.engine.hp = " + composed.engine().horsepower());

        Engine stillOwnedByCar = new Engine(200);
        CarWithOwnedEngine alsoComposition = CarWithOwnedEngine.withInjectedEngine(stillOwnedByCar);
        System.out.println("composition(injected).engine.hp = " + alsoComposition.engine().horsepower());

        Driver alice = new Driver("Alice");
        CarWithDriver aggregated = new CarWithDriver(alice);
        System.out.println("aggregation.driver = " + aggregated.driver().name());
        aggregated.assign(new Driver("Bob"));
        System.out.println("aggregation.driver after reassign = " + aggregated.driver().name());
        System.out.println("original driver still exists: " + alice.name());
    }
}
