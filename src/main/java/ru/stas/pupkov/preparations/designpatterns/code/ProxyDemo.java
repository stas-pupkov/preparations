package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy: тот же интерфейс, что у сервиса; контроль доступа / lazy / cache до делегирования.
 *
 * Spring-якорь: JDK/CGLIB proxies, AOP ({@code @Transactional}, {@code @Cacheable}).
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class ProxyDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic caching proxy ===");
        VideoService real = new ThirdPartyVideoService();
        VideoService cached = new CachingVideoProxy(real);
        System.out.println(cached.getInfo("42"));
        System.out.println(cached.getInfo("42")); // cache hit

        System.out.println();
        System.out.println("=== JDK dynamic proxy (Spring AOP style) ===");
        VideoService jdkProxy = (VideoService) Proxy.newProxyInstance(
                VideoService.class.getClassLoader(),
                new Class[]{VideoService.class},
                (proxy, method, args1) -> {
                    System.out.println("  [jdk-proxy] before " + method.getName());
                    Object result = method.invoke(real, args1);
                    System.out.println("  [jdk-proxy] after " + method.getName());
                    return result;
                });
        System.out.println(jdkProxy.getInfo("7"));

        System.out.println();
        System.out.println("=== Spring ProxyFactory ===");
        ProxyFactory pf = new ProxyFactory(new ThirdPartyVideoService());
        pf.addInterface(VideoService.class);
        pf.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            System.out.println("  [spring-aop] " + invocation.getMethod().getName());
            return invocation.proceed();
        });
        VideoService springProxy = (VideoService) pf.getProxy();
        System.out.println(springProxy.getInfo("9"));
        System.out.println("isAopProxy: " + AopUtils.isAopProxy(springProxy));
    }

    interface VideoService {
        String getInfo(String id);
    }

    static final class ThirdPartyVideoService implements VideoService {
        @Override
        public String getInfo(String id) {
            System.out.println("  [remote] fetch id=" + id);
            return "meta-" + id;
        }
    }

    static final class CachingVideoProxy implements VideoService {
        private final VideoService origin;
        private final Map<String, String> cache = new HashMap<>();

        CachingVideoProxy(VideoService origin) {
            this.origin = origin;
        }

        @Override
        public String getInfo(String id) {
            return cache.computeIfAbsent(id, origin::getInfo);
        }
    }
}
