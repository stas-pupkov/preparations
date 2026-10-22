package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator: обход коллекции без раскрытия внутренней структуры.
 *
 * Spring-якорь: {@link Page} / {@code Slice} — итерация по страницам результата;
 * JDK {@link Iterator} / Stream — стандартный контракт.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class IteratorDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic custom iterator (social graph) ===");
        ProfileCollection network = new InMemoryNetwork(List.of(
                new Profile("1", "alice@ex.com"),
                new Profile("2", "bob@ex.com"),
                new Profile("3", "carol@ex.com")));
        ProfileIterator it = network.createFriendsIterator("1");
        while (it.hasMore()) {
            System.out.println("  friend: " + it.getNext().email());
        }

        System.out.println();
        System.out.println("=== JDK Iterator ===");
        Iterator<String> jdk = List.of("a", "b").iterator();
        jdk.forEachRemaining(s -> System.out.println("  " + s));

        System.out.println();
        System.out.println("=== Spring Data Page as paginated iteration ===");
        List<String> all = List.of("p1", "p2", "p3", "p4", "p5");
        Page<String> page = new PageImpl<>(all.subList(0, 2), PageRequest.of(0, 2), all.size());
        System.out.println("  page0 content=" + page.getContent()
                + " total=" + page.getTotalElements()
                + " hasNext=" + page.hasNext());
    }

    record Profile(String id, String email) {
    }

    interface ProfileIterator {
        Profile getNext();

        boolean hasMore();
    }

    interface ProfileCollection {
        ProfileIterator createFriendsIterator(String profileId);
    }

    static final class InMemoryNetwork implements ProfileCollection {
        private final List<Profile> profiles;

        InMemoryNetwork(List<Profile> profiles) {
            this.profiles = new ArrayList<>(profiles);
        }

        @Override
        public ProfileIterator createFriendsIterator(String profileId) {
            // упрощение: все кроме себя — «друзья»
            List<Profile> friends = profiles.stream()
                    .filter(p -> !p.id().equals(profileId))
                    .toList();
            return new ListProfileIterator(friends);
        }
    }

    static final class ListProfileIterator implements ProfileIterator {
        private final List<Profile> data;
        private int index;

        ListProfileIterator(List<Profile> data) {
            this.data = data;
        }

        @Override
        public Profile getNext() {
            if (!hasMore()) {
                throw new NoSuchElementException();
            }
            return data.get(index++);
        }

        @Override
        public boolean hasMore() {
            return index < data.size();
        }
    }
}
