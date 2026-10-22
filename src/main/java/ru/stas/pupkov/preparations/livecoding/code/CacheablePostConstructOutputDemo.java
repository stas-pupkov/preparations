package ru.stas.pupkov.preparations.livecoding.code;

import jakarta.annotation.PostConstruct;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Аналог задачи: {@code @Cacheable}/{@code @CacheEvict} из {@code @PostConstruct} через {@code this}
 * → proxy не участвует → печатается {@code 0}, {@code 1}, {@code 2}.
 */
public class CacheablePostConstructOutputDemo {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class);
        // бин уже выполнил @PostConstruct при создании
        ctx.getBean(ScoringBean.class);
        ctx.close();
    }

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("counter");
        }

        @Bean
        ScoringBean scoringBean() {
            return new ScoringBean();
        }
    }

    static class ScoringBean {
        private int counter;

        @Cacheable("counter")
        public Integer scoring() {
            return counter++;
        }

        @CacheEvict("counter")
        public void clearCache() {
        }

        @PostConstruct
        public void init() {
            System.out.println("scoring: " + scoring());
            System.out.println("scoring: " + scoring());
            clearCache();
            System.out.println("scoring: " + scoring());
        }
    }
}
