package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adapter: перевод несовместимого интерфейса в ожидаемый клиентом.
 *
 * Spring-якорь: {@link HandlerAdapter} адаптирует разные типы handlers
 * (Controller, HttpRequestHandler, …) к единому контракту DispatcherServlet.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class AdapterDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic object adapter ===");
        AnalyticsClient client = new AnalyticsClient();
        client.analyzeXml("<ticks><t>1</t></ticks>", new XmlToJsonAnalyticsAdapter(new JsonAnalyticsLib()));

        System.out.println();
        System.out.println("=== Spring: HandlerAdapter idea (simplified) ===");
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(Config.class)) {
            SimpleHandlerAdapter adapter = ctx.getBean(SimpleHandlerAdapter.class);
            Controller handler = ctx.getBean(Controller.class);
            System.out.println("supports Controller: " + adapter.supports(handler));
            System.out.println("adapter class: " + adapter.getClass().getSimpleName());
        }
    }

    interface XmlAnalytics {
        void analyze(String xml);
    }

    /** Сторонний сервис с другим контрактом. */
    static final class JsonAnalyticsLib {
        void analyzeJson(String json) {
            System.out.println("  JsonAnalyticsLib got: " + json);
        }
    }

    static final class XmlToJsonAnalyticsAdapter implements XmlAnalytics {
        private final JsonAnalyticsLib lib;

        XmlToJsonAnalyticsAdapter(JsonAnalyticsLib lib) {
            this.lib = lib;
        }

        @Override
        public void analyze(String xml) {
            String json = "{\"rawXml\":\"" + xml.replace("\"", "'") + "\"}";
            lib.analyzeJson(json);
        }
    }

    static final class AnalyticsClient {
        void analyzeXml(String xml, XmlAnalytics analytics) {
            analytics.analyze(xml);
        }
    }

    /**
     * Упрощённый HandlerAdapter: умеет только {@link Controller}.
     * Полноценный MVC-стек здесь не поднимаем.
     */
    static final class SimpleHandlerAdapter implements HandlerAdapter {
        @Override
        public boolean supports(Object handler) {
            return handler instanceof Controller;
        }

        @Override
        public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            return ((Controller) handler).handleRequest(request, response);
        }
    }

    @Configuration
    static class Config {
        @Bean
        SimpleHandlerAdapter handlerAdapter() {
            return new SimpleHandlerAdapter();
        }

        @Bean
        Controller demoController() {
            return (req, res) -> new ModelAndView("ok");
        }
    }
}
