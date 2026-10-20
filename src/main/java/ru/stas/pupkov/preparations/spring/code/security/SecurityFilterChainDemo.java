package ru.stas.pupkov.preparations.spring.code.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Минимальный servlet Security: {@code SecurityFilterChain} + httpBasic + method security.
 * Отдельный пакет — не пересекается со сканом других демо.
 *
 * Запуск: из корня проекта после compileJava.
 */
@SpringBootApplication
public class SecurityFilterChainDemo {

    public static void main(String[] args) {
        System.setProperty("server.port", "0");
        try (ConfigurableApplicationContext ctx = SpringApplication.run(SecurityFilterChainDemo.class, args)) {
            int port = ((WebServerApplicationContext) ctx).getWebServer().getPort();
            String base = "http://localhost:" + port;
            RestClient client = RestClient.create();

            System.out.println("=== 1. without auth → 401 ===");
            try {
                client.get().uri(base + "/api/me").retrieve().toBodilessEntity();
            } catch (Exception e) {
                System.out.println("  " + e.getClass().getSimpleName() + ": " + rootMessage(e));
            }

            System.out.println("=== 2. user/password → 200 + principal ===");
            String body = client.get()
                    .uri(base + "/api/me")
                    .header("Authorization", basic("user", "password"))
                    .retrieve()
                    .body(String.class);
            System.out.println("  " + body);

            System.out.println("=== 3. user → /api/admin → 403 ===");
            try {
                client.get()
                        .uri(base + "/api/admin")
                        .header("Authorization", basic("user", "password"))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                System.out.println("  " + e.getClass().getSimpleName() + ": " + rootMessage(e));
            }

            System.out.println("=== 4. admin → /api/admin → 200 ===");
            String admin = client.get()
                    .uri(base + "/api/admin")
                    .header("Authorization", basic("admin", "password"))
                    .retrieve()
                    .body(String.class);
            System.out.println("  " + admin);
        }
    }

    private static String basic(String user, String password) {
        String raw = user + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/public").permitAll()
                            .requestMatchers("/api/admin").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        UserDetailsService users(PasswordEncoder encoder) {
            UserDetails user = User.builder()
                    .username("user")
                    .password(encoder.encode("password"))
                    .roles("USER")
                    .build();
            UserDetails admin = User.builder()
                    .username("admin")
                    .password(encoder.encode("password"))
                    .roles("USER", "ADMIN")
                    .build();
            return new InMemoryUserDetailsManager(user, admin);
        }
    }

    @RestController
    static class ApiController {

        @GetMapping("/api/public")
        String pub() {
            return "public";
        }

        @GetMapping("/api/me")
        String me(@AuthenticationPrincipal UserDetails principal) {
            return "hello " + principal.getUsername() + " authorities=" + principal.getAuthorities();
        }

        @GetMapping("/api/admin")
        ResponseEntity<String> admin() {
            return ResponseEntity.ok("admin-only");
        }
    }
}
