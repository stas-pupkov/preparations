package ru.stas.pupkov.preparations.jvm.code;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.Comparator;
import java.util.Locale;

/**
 * Обзор runtime memory pools HotSpot через MXBean: heap, Metaspace, Code Cache.
 *
 * <p>Имена пулов зависят от JVM/GC; смотрим типичные для OpenJDK 17.
 */
public class RuntimeDataAreasDemo {

    public static void main(String[] args) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        printUsage("Heap", memory.getHeapMemoryUsage());
        printUsage("Non-heap (sum)", memory.getNonHeapMemoryUsage());

        System.out.println("--- memory pools ---");
        ManagementFactory.getMemoryPoolMXBeans().stream()
                .sorted(Comparator.comparing(MemoryPoolMXBean::getName))
                .forEach(pool -> {
                    MemoryUsage usage = pool.getUsage();
                    if (usage == null) {
                        return;
                    }
                    System.out.printf(
                            "%-28s type=%-10s used=%s max=%s%n",
                            pool.getName(),
                            pool.getType(),
                            human(usage.getUsed()),
                            human(usage.getMax())
                    );
                });

        System.out.println("--- notes ---");
        System.out.println("Metaspace ≈ method area metadata (native), not Java heap objects");
        System.out.println("CodeHeap* ≈ JIT code cache");
        System.out.println("Compressed Class Space — часть Metaspace при UseCompressedClassPointers");
    }

    private static void printUsage(String title, MemoryUsage usage) {
        System.out.printf(
                "%s: used=%s committed=%s max=%s%n",
                title,
                human(usage.getUsed()),
                human(usage.getCommitted()),
                human(usage.getMax())
        );
    }

    private static String human(long bytes) {
        if (bytes < 0) {
            return "undefined";
        }
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.1f MiB", mb);
    }
}
