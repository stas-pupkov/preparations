package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Кардинальность JPA-ассоциаций и где в БД живёт FK / join table (учебная модель без Hibernate).
 */
public class AssociationTypesDemo {

    public static void main(String[] args) {
        manyToOneAndOneToMany();
        oneToOne();
        manyToMany();
    }

    private static void manyToOneAndOneToMany() {
        System.out.println("=== @ManyToOne / @OneToMany ===");
        Author author = new Author(1L, "Tolkien");
        Book book = new Book(10L, "LOTR");
        book.author = author;                 // owning (@ManyToOne) → book.author_id
        author.books.add(book);               // inverse (@OneToMany mappedBy)

        System.out.println("SQL mental model:");
        System.out.println("  book.author_id = " + book.author.id + "  -- FK на стороне «многих»");
        System.out.println("  author table: нет колонки books");
        System.out.println("author.books size=" + author.books.size());
    }

    private static void oneToOne() {
        System.out.println();
        System.out.println("=== @OneToOne ===");
        User user = new User(1L, "stas");
        Profile profile = new Profile(1L, "bio");
        profile.user = user;                  // owning → profile.user_id (unique)
        user.profile = profile;               // inverse mappedBy

        System.out.println("SQL mental model:");
        System.out.println("  profile.user_id = " + profile.user.id + "  -- FK (+ UNIQUE) у owning");
    }

    private static void manyToMany() {
        System.out.println();
        System.out.println("=== @ManyToMany ===");
        Student s = new Student(1L, "Ada");
        Course c = new Course(2L, "JPA");
        s.courses.add(c);                     // owning → строки join table
        c.students.add(s);                    // inverse

        System.out.println("SQL mental model:");
        System.out.println("  INSERT INTO student_course(student_id, course_id) VALUES ("
                + s.id + ", " + c.id + ")");
        System.out.println("  student/course tables: без прямого FK друг на друга");
        System.out.println("На проде чаще link-entity Enrollment вместо чистого @ManyToMany.");
    }

    static final class Author {
        final Long id;
        final String name;
        final List<Book> books = new ArrayList<>();

        Author(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Book {
        final Long id;
        final String title;
        Author author;

        Book(Long id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    static final class User {
        final Long id;
        final String login;
        Profile profile;

        User(Long id, String login) {
            this.id = id;
            this.login = login;
        }
    }

    static final class Profile {
        final Long id;
        final String bio;
        User user;

        Profile(Long id, String bio) {
            this.id = id;
            this.bio = bio;
        }
    }

    static final class Student {
        final Long id;
        final String name;
        final Set<Course> courses = new HashSet<>();

        Student(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Course {
        final Long id;
        final String title;
        final Set<Student> students = new HashSet<>();

        Course(Long id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
