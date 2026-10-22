package ru.stas.pupkov.preparations.designpatterns.code;

/**
 * Bridge: две независимые иерархии (абстракция × реализация) связаны композицией.
 *
 * Spring-якорь: слой абстракции ({@code PlatformTransactionManager} API) vs
 * реализации (DataSourceTM / JpaTM) — развитие осей отдельно.
 * Здесь — чистая модель remote/device без поднятия TX-менеджера.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class BridgeDemo {

    public static void main(String[] args) {
        System.out.println("=== Bridge: Remote (abstraction) × Device (implementation) ===");
        Device tv = new Tv();
        Remote basic = new Remote(tv);
        basic.togglePower();
        basic.volumeUp();

        Device radio = new Radio();
        AdvancedRemote advanced = new AdvancedRemote(radio);
        advanced.togglePower();
        advanced.mute();

        System.out.println();
        System.out.println("=== Same idea: TxFacade × TxSpi (Spring-like naming) ===");
        TxFacade jpa = new TxFacade(new JpaTxSpi());
        TxFacade jdbc = new TxFacade(new JdbcTxSpi());
        jpa.runInTx(() -> System.out.println("  business work"));
        jdbc.runInTx(() -> System.out.println("  business work"));
    }

    interface Device {
        boolean isEnabled();

        void enable();

        void disable();

        int getVolume();

        void setVolume(int percent);
    }

    static final class Tv implements Device {
        private boolean on;
        private int volume = 30;

        @Override
        public boolean isEnabled() {
            return on;
        }

        @Override
        public void enable() {
            on = true;
            System.out.println("  TV on");
        }

        @Override
        public void disable() {
            on = false;
            System.out.println("  TV off");
        }

        @Override
        public int getVolume() {
            return volume;
        }

        @Override
        public void setVolume(int percent) {
            volume = percent;
            System.out.println("  TV volume=" + volume);
        }
    }

    static final class Radio implements Device {
        private boolean on;
        private int volume = 20;

        @Override
        public boolean isEnabled() {
            return on;
        }

        @Override
        public void enable() {
            on = true;
            System.out.println("  Radio on");
        }

        @Override
        public void disable() {
            on = false;
            System.out.println("  Radio off");
        }

        @Override
        public int getVolume() {
            return volume;
        }

        @Override
        public void setVolume(int percent) {
            volume = percent;
            System.out.println("  Radio volume=" + volume);
        }
    }

    static class Remote {
        protected final Device device;

        Remote(Device device) {
            this.device = device;
        }

        void togglePower() {
            if (device.isEnabled()) {
                device.disable();
            } else {
                device.enable();
            }
        }

        void volumeUp() {
            device.setVolume(device.getVolume() + 10);
        }
    }

    static final class AdvancedRemote extends Remote {
        AdvancedRemote(Device device) {
            super(device);
        }

        void mute() {
            device.setVolume(0);
            System.out.println("  muted");
        }
    }

    interface TxSpi {
        void begin();

        void commit();
    }

    static final class JpaTxSpi implements TxSpi {
        @Override
        public void begin() {
            System.out.println("  [JpaTxSpi] begin");
        }

        @Override
        public void commit() {
            System.out.println("  [JpaTxSpi] commit");
        }
    }

    static final class JdbcTxSpi implements TxSpi {
        @Override
        public void begin() {
            System.out.println("  [JdbcTxSpi] begin");
        }

        @Override
        public void commit() {
            System.out.println("  [JdbcTxSpi] commit");
        }
    }

    static final class TxFacade {
        private final TxSpi spi;

        TxFacade(TxSpi spi) {
            this.spi = spi;
        }

        void runInTx(Runnable work) {
            spi.begin();
            work.run();
            spi.commit();
        }
    }
}
