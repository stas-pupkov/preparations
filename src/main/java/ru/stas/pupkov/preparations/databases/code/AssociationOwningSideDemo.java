package ru.stas.pupkov.preparations.databases.code;

import java.util.ArrayList;
import java.util.List;

/**
 * Owning vs inverse side: в БД FK пишет только owning side; Java-коллекция inverse сама по себе SQL не меняет.
 */
public class AssociationOwningSideDemo {

    static final class Author {
        final String name;
        final List<Book> books = new ArrayList<>(); // inverse: mappedBy = "author"

        Author(String name) {
            this.name = name;
        }
    }

    static final class Book {
        final String title;
        Author author; // owning: @ManyToOne, колонка author_id

        Book(String title) {
            this.title = title;
        }
    }

    static final class FakeFlush {
        void flush(Book book) {
            Long authorId = book.author == null ? null : (long) book.author.name.hashCode();
            System.out.println("SQL: UPDATE book SET author_id=" + authorId
                    + " WHERE title='" + book.title + "'");
        }
    }

    public static void main(String[] args) {
        Author author = new Author("Tolkien");
        Book book = new Book("LOTR");
        FakeFlush flush = new FakeFlush();

        System.out.println("=== Only inverse side set (common bug) ===");
        author.books.add(book); // заполнили только mappedBy-сторону
        // book.author всё ещё null
        flush.flush(book);
        System.out.println("In-memory author.books=" + author.books.size()
                + ", but FK not set -> relation NOT persisted");

        System.out.println();
        System.out.println("=== Owning side set ===");
        book.author = author;
        flush.flush(book);
        System.out.println("FK written. Helper sync both sides in domain methods:");

        System.out.println();
        Book book2 = new Book("Hobbit");
        addBook(author, book2);
        flush.flush(book2);
        System.out.println("author.books titles="
                + author.books.stream().map(b -> b.title).toList());
    }

    /** Правильный helper: синхронизирует обе стороны в памяти. */
    static void addBook(Author author, Book book) {
        author.books.add(book);
        book.author = author;
    }
}
