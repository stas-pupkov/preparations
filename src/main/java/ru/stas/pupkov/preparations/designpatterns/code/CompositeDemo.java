package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite: дерево объектов с единым интерфейсом листа и узла.
 *
 * Spring-якорь: иерархии ролей / меню / resource trees — клиент трактует
 * узел и лист одинаково. Здесь дополнительно показан RoleHierarchy как дерево прав.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class CompositeDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Composite (order boxes) ===");
        Product book = new Product("Book", 500);
        Product pen = new Product("Pen", 50);
        Box small = new Box("small");
        small.add(pen);
        Box order = new Box("order");
        order.add(book);
        order.add(small);
        System.out.println(order.name() + " total=" + order.price());

        System.out.println();
        System.out.println("=== Spring RoleHierarchy (tree of authorities) ===");
        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy(
                "ROLE_ADMIN > ROLE_STAFF\nROLE_STAFF > ROLE_USER");
        var reachable = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        System.out.println("ADMIN reaches: " + reachable);
    }

    interface Item {
        String name();

        int price();
    }

    static final class Product implements Item {
        private final String name;
        private final int price;

        Product(String name, int price) {
            this.name = name;
            this.price = price;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int price() {
            return price;
        }
    }

    static final class Box implements Item {
        private final String name;
        private final List<Item> children = new ArrayList<>();

        Box(String name) {
            this.name = name;
        }

        void add(Item item) {
            children.add(item);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int price() {
            int sum = 0;
            for (Item child : children) {
                sum += child.price();
            }
            return sum;
        }
    }
}
