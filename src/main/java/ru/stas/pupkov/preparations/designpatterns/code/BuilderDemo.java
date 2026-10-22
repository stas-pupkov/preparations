package ru.stas.pupkov.preparations.designpatterns.code;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builder: пошаговая сборка сложного объекта; один процесс — разные представления.
 *
 * Spring-якорь: {@link UriComponentsBuilder}, {@code WebClient.builder()},
 * исторически {@code RestTemplateBuilder} — тот же intent.
 *
 * Запуск: из корня проекта после compileJava.
 */
public final class BuilderDemo {

    public static void main(String[] args) {
        System.out.println("=== Classic Builder (+ Director) ===");
        Director director = new Director();
        HouseBuilder wooden = new WoodenHouseBuilder();
        director.constructCottage(wooden);
        System.out.println(wooden.getResult());

        HouseBuilder stone = new StoneHouseBuilder();
        director.constructVilla(stone);
        System.out.println(stone.getResult());

        System.out.println();
        System.out.println("=== Fluent builder (типичный Java-стиль) ===");
        HttpRequest req = HttpRequest.builder()
                .method("POST")
                .url("/orders")
                .header("Content-Type", "application/json")
                .body("{\"id\":1}")
                .build();
        System.out.println(req);

        System.out.println();
        System.out.println("=== Spring UriComponentsBuilder ===");
        String uri = UriComponentsBuilder
                .fromPath("/orders/{id}")
                .scheme("https")
                .host("api.example.com")
                .queryParam("expand", "items")
                .buildAndExpand(42)
                .toUriString();
        System.out.println(uri);
    }

    record House(String walls, String roof, boolean garden, boolean pool) {
    }

    interface HouseBuilder {
        void buildWalls();

        void buildRoof();

        void buildGarden();

        void buildPool();

        House getResult();
    }

    static final class WoodenHouseBuilder implements HouseBuilder {
        private String walls;
        private String roof;
        private boolean garden;
        private boolean pool;

        @Override
        public void buildWalls() {
            walls = "wood";
        }

        @Override
        public void buildRoof() {
            roof = "shingle";
        }

        @Override
        public void buildGarden() {
            garden = true;
        }

        @Override
        public void buildPool() {
            pool = false;
        }

        @Override
        public House getResult() {
            return new House(walls, roof, garden, pool);
        }
    }

    static final class StoneHouseBuilder implements HouseBuilder {
        private String walls;
        private String roof;
        private boolean garden;
        private boolean pool;

        @Override
        public void buildWalls() {
            walls = "stone";
        }

        @Override
        public void buildRoof() {
            roof = "tile";
        }

        @Override
        public void buildGarden() {
            garden = true;
        }

        @Override
        public void buildPool() {
            pool = true;
        }

        @Override
        public House getResult() {
            return new House(walls, roof, garden, pool);
        }
    }

    static final class Director {
        void constructCottage(HouseBuilder b) {
            b.buildWalls();
            b.buildRoof();
            b.buildGarden();
        }

        void constructVilla(HouseBuilder b) {
            b.buildWalls();
            b.buildRoof();
            b.buildGarden();
            b.buildPool();
        }
    }

    static final class HttpRequest {
        private final String method;
        private final String url;
        private final String headers;
        private final String body;

        private HttpRequest(String method, String url, String headers, String body) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
        }

        static Builder builder() {
            return new Builder();
        }

        @Override
        public String toString() {
            return method + " " + url + " headers=[" + headers + "] body=" + body;
        }

        static final class Builder {
            private String method = "GET";
            private String url = "/";
            private final StringBuilder headers = new StringBuilder();
            private String body = "";

            Builder method(String method) {
                this.method = method;
                return this;
            }

            Builder url(String url) {
                this.url = url;
                return this;
            }

            Builder header(String name, String value) {
                if (headers.length() > 0) {
                    headers.append("; ");
                }
                headers.append(name).append('=').append(value);
                return this;
            }

            Builder body(String body) {
                this.body = body;
                return this;
            }

            HttpRequest build() {
                return new HttpRequest(method, url, headers.toString(), body);
            }
        }
    }
}
