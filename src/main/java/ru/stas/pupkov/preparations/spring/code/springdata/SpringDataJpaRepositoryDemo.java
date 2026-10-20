package ru.stas.pupkov.preparations.spring.code.springdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA: интерфейс-репозиторий → proxy → derived / {@code @Query}.
 * Отдельный пакет, чтобы component scan не подхватывал соседние демо.
 * Security auto-config отключён — иначе starter-security мешает этому демо.
 *
 * Запуск: из корня проекта после compileJava.
 */
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
@EnableJpaRepositories
@EntityScan
public class SpringDataJpaRepositoryDemo {

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:springdata;DB_CLOSE_DELAY=-1");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        System.setProperty("spring.jpa.show-sql", "true");

        try (ConfigurableApplicationContext ctx =
                     SpringApplication.run(SpringDataJpaRepositoryDemo.class, args)) {
            ctx.getBean(UserService.class).seedAndQuery();
        }
    }

    @Service
    static class UserService {

        private final DemoUserRepository users;

        UserService(DemoUserRepository users) {
            this.users = users;
        }

        @Transactional
        void seedAndQuery() {
            users.save(new DemoUser("a@example.com", true));
            users.save(new DemoUser("b@example.com", false));

            System.out.println("=== derived: findByEmailAndActiveTrue ===");
            System.out.println("  " + users.findByEmailAndActiveTrue("a@example.com"));
            System.out.println("  inactive match: " + users.findByEmailAndActiveTrue("b@example.com"));

            System.out.println("=== @Query: byEmailIgnoreCase ===");
            System.out.println("  " + users.byEmailIgnoreCase("A@EXAMPLE.COM"));

            System.out.println("=== derived list: findByActiveTrue ===");
            users.findByActiveTrue().forEach(u -> System.out.println("  " + u));

            System.out.println("repository proxy class = " + users.getClass().getName());
        }
    }
}
