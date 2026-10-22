package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flyweight: shared intrinsic state + external context; экономия памяти.
 *
 * Spring-якорь: кеширующие map'ы вроде {@link ConcurrentReferenceHashMap};
 * String pool / Integer cache — родственная идея на уровне JVM.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class FlyweightDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Flyweight (forest) ===");
        Forest forest = new Forest();
        forest.plantTree(1, 1, "Oak", "green", "oak-tex");
        forest.plantTree(2, 3, "Oak", "green", "oak-tex");
        forest.plantTree(5, 8, "Pine", "dark", "pine-tex");
        forest.draw();
        System.out.println("tree types cached: " + TreeFactory.size());

        System.out.println();
        System.out.println("=== Spring ConcurrentReferenceHashMap as soft cache ===");
        Map<String, byte[]> cache = new ConcurrentReferenceHashMap<>();
        cache.put("icon-a", new byte[1024]);
        System.out.println("cached keys: " + cache.keySet());
    }

    /** Intrinsic (shared, immutable). */
    static final class TreeType {
        private final String name;
        private final String color;
        private final String texture;

        TreeType(String name, String color, String texture) {
            this.name = name;
            this.color = color;
            this.texture = texture;
        }

        void draw(int x, int y) {
            System.out.println("  draw " + name + "/" + color + " @" + x + "," + y
                    + " tex=" + texture);
        }
    }

    static final class TreeFactory {
        private static final Map<String, TreeType> TYPES = new ConcurrentHashMap<>();

        static TreeType getTreeType(String name, String color, String texture) {
            String key = name + "|" + color + "|" + texture;
            return TYPES.computeIfAbsent(key, k -> new TreeType(name, color, texture));
        }

        static int size() {
            return TYPES.size();
        }
    }

    /** Extrinsic context. */
    static final class Tree {
        private final int x;
        private final int y;
        private final TreeType type;

        Tree(int x, int y, TreeType type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }

        void draw() {
            type.draw(x, y);
        }
    }

    static final class Forest {
        private final List<Tree> trees = new ArrayList<>();

        void plantTree(int x, int y, String name, String color, String texture) {
            TreeType type = TreeFactory.getTreeType(name, color, texture);
            trees.add(new Tree(x, y, type));
        }

        void draw() {
            trees.forEach(Tree::draw);
        }
    }
}
