package ru.stas.pupkov.preparations.databases.code;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Модель candidate / primary / alternate / foreign key и связей 1:N, M:N.
 */
public class KeysAndRelationshipsDemo {

    record User(long id, String email, String login) {
    }

    record Order(long id, long userId, String title) {
    }

    record FacultyProfessor(long facultyId, long professorId) {
    }

    public static void main(String[] args) {
        Map<Long, User> usersByPk = new HashMap<>();
        Map<String, Long> usersByEmail = new HashMap<>(); // alternate key
        Map<String, Long> usersByLogin = new HashMap<>(); // alternate key

        User alice = new User(1L, "alice@example.com", "alice");
        insertUser(usersByPk, usersByEmail, usersByLogin, alice);
        System.out.println("PK lookup: " + usersByPk.get(1L));
        System.out.println("alternate email: " + usersByPk.get(usersByEmail.get("alice@example.com")));

        try {
            insertUser(usersByPk, usersByEmail, usersByLogin,
                    new User(2L, "alice@example.com", "alice2"));
        } catch (IllegalStateException e) {
            System.out.println("alternate key violation: " + e.getMessage());
        }

        Map<Long, Order> orders = new HashMap<>();
        insertOrder(orders, usersByPk, new Order(10L, 1L, "Book"));
        try {
            insertOrder(orders, usersByPk, new Order(11L, 99L, "Orphan"));
        } catch (IllegalStateException e) {
            System.out.println("FK violation: " + e.getMessage());
        }
        System.out.println("1:N orders of user 1: " + orders.values().stream()
                .filter(o -> o.userId() == 1L)
                .toList());

        Set<FacultyProfessor> link = new HashSet<>();
        link.add(new FacultyProfessor(1L, 100L));
        link.add(new FacultyProfessor(1L, 101L));
        link.add(new FacultyProfessor(2L, 100L));
        System.out.println("M:N link rows: " + link);
        System.out.println("faculties of professor 100: " + link.stream()
                .filter(r -> r.professorId() == 100L)
                .map(FacultyProfessor::facultyId)
                .toList());
    }

    private static void insertUser(Map<Long, User> byPk,
                                   Map<String, Long> byEmail,
                                   Map<String, Long> byLogin,
                                   User user) {
        if (byPk.containsKey(user.id())) {
            throw new IllegalStateException("PK " + user.id());
        }
        if (byEmail.containsKey(user.email())) {
            throw new IllegalStateException("UNIQUE email " + user.email());
        }
        if (byLogin.containsKey(user.login())) {
            throw new IllegalStateException("UNIQUE login " + user.login());
        }
        byPk.put(user.id(), user);
        byEmail.put(user.email(), user.id());
        byLogin.put(user.login(), user.id());
    }

    private static void insertOrder(Map<Long, Order> orders, Map<Long, User> users, Order order) {
        if (!users.containsKey(order.userId())) {
            throw new IllegalStateException("FK user_id=" + order.userId());
        }
        if (orders.putIfAbsent(order.id(), order) != null) {
            throw new IllegalStateException("PK order " + order.id());
        }
    }
}
