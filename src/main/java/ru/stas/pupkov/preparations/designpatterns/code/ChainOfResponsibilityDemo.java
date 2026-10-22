package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Chain of Responsibility: запрос идёт по цепочке; обработчик решает — обработать / передать.
 *
 * Spring-якорь: Servlet Filter chain, Security filter chain, {@code HandlerInterceptor}.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class ChainOfResponsibilityDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic CoR (auth → role → throttle) ===");
        Handler chain = Handler.link(
                new AuthHandler(),
                new RoleHandler(),
                new ThrottleHandler());
        chain.handle(new Request("alice", "USER", 1));
        chain.handle(new Request(null, "USER", 1));
        chain.handle(new Request("bob", "GUEST", 1));
        chain.handle(new Request("carol", "USER", 100));

        System.out.println();
        System.out.println("=== Spring Filter as chain node (structure only) ===");
        List<OncePerRequestFilter> filters = List.of(new LoggingFilter(), new HeaderFilter());
        System.out.println("filters in order: " + filters.stream()
                .map(f -> f.getClass().getSimpleName())
                .toList());
    }

    record Request(String user, String role, int rpm) {
    }

    abstract static class Handler {
        private Handler next;

        static Handler link(Handler first, Handler... chain) {
            Handler head = first;
            for (Handler nextInChain : chain) {
                head.next = nextInChain;
                head = nextInChain;
            }
            return first;
        }

        final void handle(Request request) {
            if (doHandle(request) && next != null) {
                next.handle(request);
            }
        }

        /** @return true — передать дальше */
        abstract boolean doHandle(Request request);
    }

    static final class AuthHandler extends Handler {
        @Override
        boolean doHandle(Request request) {
            if (request.user() == null) {
                System.out.println("  DENY: unauthenticated");
                return false;
            }
            System.out.println("  AUTH ok: " + request.user());
            return true;
        }
    }

    static final class RoleHandler extends Handler {
        @Override
        boolean doHandle(Request request) {
            if (!"USER".equals(request.role()) && !"ADMIN".equals(request.role())) {
                System.out.println("  DENY: role " + request.role());
                return false;
            }
            System.out.println("  ROLE ok: " + request.role());
            return true;
        }
    }

    static final class ThrottleHandler extends Handler {
        @Override
        boolean doHandle(Request request) {
            if (request.rpm() > 60) {
                System.out.println("  DENY: throttle");
                return false;
            }
            System.out.println("  THROTTLE ok, accept request");
            return true;
        }
    }

    @Order(1)
    static final class LoggingFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            filterChain.doFilter(request, response);
        }
    }

    @Order(2)
    static final class HeaderFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            filterChain.doFilter(request, response);
        }
    }
}
