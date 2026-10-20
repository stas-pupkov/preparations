package ru.stas.pupkov.preparations.spring.code;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Несколько {@code @Qualifier} на точке внедрения = AND (идея из spring-beans/listinjection).
 * Composed qualifier {@code @AnyGenre} «наследует» все жанры через мета-аннотации.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class MultiQualifierListInjectionDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        Film film = ctx.getBean(Film.class);
        System.out.println("Actors with @Comedy AND @Action: "
                + film.actors().stream().map(Actor::name).collect(Collectors.joining(", ")));
        ctx.close();
    }

    interface Actor {
        String name();
    }

    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    @interface Comedy {
    }

    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    @interface Action {
    }

    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    @interface Melodrama {
    }

    /** Актёр «всех жанров»: подходит под любой набор qualifier'ов из тройки. */
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Comedy
    @Action
    @Melodrama
    @Qualifier
    @interface AnyGenre {
    }

    static final class Film {

        private final List<Actor> actors;

        @Autowired
        Film(@Comedy @Action List<Actor> actors) {
            this.actors = actors;
        }

        List<Actor> actors() {
            return actors;
        }
    }

    @Configuration
    static class Config {

        @Bean
        @Comedy
        Actor jim() {
            return () -> "Jim";
        }

        @Bean
        @Action
        Actor tobey() {
            return () -> "Tobey";
        }

        @Bean
        @Action
        @Comedy
        Actor stallone() {
            return () -> "Stallone";
        }

        @Bean
        @Melodrama
        Actor julia() {
            return () -> "Julia";
        }

        @Bean
        @AnyGenre
        Actor chuck() {
            return () -> "Chuck";
        }

        @Bean
        Actor katy() {
            return () -> "Katy"; // без qualifier — не попадёт в @Comedy @Action List
        }

        @Bean
        Film film(@Comedy @Action List<Actor> actors) {
            return new Film(actors);
        }
    }
}
