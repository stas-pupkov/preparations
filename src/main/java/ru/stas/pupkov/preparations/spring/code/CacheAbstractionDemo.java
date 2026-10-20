package ru.stas.pupkov.preparations.spring.code;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spring Cache abstraction: {@code @Cacheable} / {@code @CacheEvict} через AOP-прокси.
 * Self-invocation {@code this.find} не попадает в кэш; вызов через {@code self} — попадает.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class CacheAbstractionDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        UserService users = ctx.getBean(UserService.class);

        System.out.println("=== 1. miss → hit через proxy ===");
        System.out.println("  " + users.findById(1L));
        System.out.println("  " + users.findById(1L)); // без "load from DB"

        System.out.println();
        System.out.println("=== 2. self-invocation: this.findById мимо кэша ===");
        users.loadViaSelf(2L);

        System.out.println();
        System.out.println("=== 3. через self-proxy — кэш работает ===");
        users.loadViaProxy(3L);
        users.loadViaProxy(3L);

        System.out.println();
        System.out.println("=== 4. @CacheEvict ===");
        users.findById(1L);
        users.evict(1L);
        System.out.println("  after evict: " + users.findById(1L)); // снова load

        ctx.close();
    }

    static class UserService {

        @Autowired
        @Lazy
        private UserService self;

        private int loads;

        @Cacheable(cacheNames = "users", key = "#id")
        public String findById(long id) {
            loads++;
            System.out.println("  load from DB id=" + id + " (loads=" + loads + ")");
            return "user-" + id;
        }

        void loadViaSelf(long id) {
            System.out.println("  this.findById:");
            System.out.println("    " + findById(id));
            System.out.println("  this.findById again:");
            System.out.println("    " + findById(id)); // снова load
        }

        void loadViaProxy(long id) {
            System.out.println("  self.findById:");
            System.out.println("    " + self.findById(id));
            System.out.println("  self.findById again:");
            System.out.println("    " + self.findById(id));
        }

        @CacheEvict(cacheNames = "users", key = "#id")
        public void evict(long id) {
            System.out.println("  evict id=" + id);
        }
    }

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("users");
        }

        @Bean
        UserService userService() {
            return new UserService();
        }
    }
}
