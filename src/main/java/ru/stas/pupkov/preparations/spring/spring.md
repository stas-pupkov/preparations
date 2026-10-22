# Spring Framework / Spring Boot

Java 17+, Spring Framework 7.x / Spring Boot 4.x (toolchain проекта). Заметка для изучения и
подготовки к собеседованиям.

Связанные темы: IoC/DI на уровне дизайна — [oop.md](../oop/oop.md); persistence context /
TX / N+1 — [jpa-hibernate.md](../databases/jpa-hibernate.md) (Spring Data JPA там в §11).

## Содержание

1. [IoC, DI и контейнер](#1-ioc-di-и-контейнер)
2. [Регистрация бинов и стереотипы](#2-регистрация-бинов-и-стереотипы)
3. [Жизненный цикл бина](#3-жизненный-цикл-бина)
4. [Области видимости (scopes)](#4-области-видимости-scopes)
5. [Разрешение зависимостей](#5-разрешение-зависимостей)
6. [AOP](#6-aop)
7. [Транзакции](#7-транзакции)
8. [Spring Framework vs Spring Boot](#8-spring-framework-vs-spring-boot)
9. [Кэш (Spring Cache)](#9-кэш-spring-cache)
10. [Spring Data](#10-spring-data)
11. [Spring Security](#11-spring-security)
12. [Web MVC: краткий каркас](#12-web-mvc-краткий-каркас)
13. [Профили и условия](#13-профили-и-условия)
14. [REST и SOAP](#14-rest-и-soap)
15. [HTTP cookie](#15-http-cookie)
16. [События в Spring](#16-события-в-spring)
17. [Spring Cloud Config](#17-spring-cloud-config)
18. [OpenFeign](#18-openfeign)
19. [WebSockets](#19-websockets)
20. [Spring Integration](#20-spring-integration)
21. [Spring Data REST](#21-spring-data-rest)
22. [Actuator в Docker / K8s](#22-actuator-в-docker--k8s)
23. [Демо-код](#23-демо-код)
24. [Вопросы для собеседования и ответы](#24-вопросы-для-собеседования-и-ответы)

---

## 1. IoC, DI и контейнер

**Инверсия управления (Inversion of Control, IoC)** — создание объектов, связывание их
зависимостей (wiring) и часть жизненного цикла переданы контейнеру. Код приложения не
собирает граф вручную в каждом сценарии использования (use-case).

**Внедрение зависимостей (Dependency Injection, DI)** — механизм передачи зависимостей
снаружи (через конструктор / setter / поле), а не через `new` внутри класса.

**IoC-контейнер Spring** — `BeanFactory` (базовый контракт) и `ApplicationContext`
(корпоративная надстройка: события, интернационализация (i18n), ресурсы, интеграция с
AOP/Boot). В Boot почти всегда имеем дело с `ApplicationContext`.

Способы DI:

| Способ | Когда уместен |
|--------|----------------|
| Через конструктор | по умолчанию на проде: обязательные зависимости, `final`, тестируемость |
| Через setter / метод | необязательные зависимости, поздняя переконфигурация |
| Через поле `@Autowired` | кратко в прототипах; на проде обычно избегают |

Предпочтительный вариант — конструктор (с Spring 4.3+ один конструктор можно не помечать
`@Autowired`):

```java
@Service
public class OrderService {
    private final PaymentClient payments;
    private final OrderRepository orders;

    public OrderService(PaymentClient payments, OrderRepository orders) {
        this.payments = payments;
        this.orders = orders;
    }
}
```

Setter / произвольный метод — контейнер вызовет после создания экземпляра и подставит
аргументы из контекста:

```java
@Service
public class OrderService {
    private NotificationClient notifications;

    @Autowired
    public void setNotifications(NotificationClient notifications) {
        this.notifications = notifications;
    }
}
```

Через поле (кратко, на проде обычно избегают):

```java
@Service
public class OrderService {
    @Autowired
    private PaymentClient payments;
}
```

Связь с DIP (SOLID): DI-контейнер *помогает* соблюдать DIP, но не гарантирует его. Если в
конструктор внедрили конкретную реализацию `SmtpMailer`, IoC есть, DIP — нет. Подробнее — в
oop-заметке.

---

## 2. Регистрация бинов и стереотипы

### 2.1. Как бин попадает в контейнер

1. **Сканирование компонентов (component scan)** — классы со стереотипными аннотациями
   (stereotype annotations) в указанных пакетах.
2. **`@Bean` в `@Configuration`** — фабричный метод (factory method) создаёт экземпляр явно.
3. **Импорт** — `@Import`, `@ImportAutoConfiguration`, XML (устаревший основной стиль).
4. **Программная регистрация** — `BeanDefinitionRegistry` / `GenericApplicationContext`.

`@ComponentScan` задаёт *где искать*. Без сканирования (и без других способов регистрации)
`@Service` на классе сам по себе ничего не создаёт.

В Boot точка входа обычно выглядит так — сканируется пакет `com.example.app` и все
подпакеты:

```java
package com.example.app;

@SpringBootApplication // включает @ComponentScan этого пакета
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Класс в `com.example.app.order` подхватится. Класс в `com.example.other` — нет, пока не
добавить пакет явно:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.app", "com.example.other"})
public class Application { /* ... */ }
```

### 2.2. Стереотипные аннотации (stereotype annotations)

**Стереотип** в Spring — аннотация, которая помечает класс как *роль* в архитектуре и одновременно
как кандидата на регистрацию бина при сканировании. База — `@Component`. Остальные stereotypes
мета-аннотированы `@Component` (через `@AliasFor` / композицию аннотаций) и добавляют смысл
слоя, а иногда — инфраструктурное поведение.

| Аннотация | Смысл |
|-----------|--------|
| `@Component` | базовый стереотип, кандидат в бины |
| `@Service` | бизнес-слой; сейчас в основном семантика и читаемость |
| `@Repository` | слой доступа к данным + **трансляция исключений (exception translation)** в иерархию `DataAccessException` |
| `@Controller` | MVC-контроллер (обычно представление / HTML) |
| `@RestController` | `@Controller` + `@ResponseBody` (тело ответа через HttpMessageConverter) |

Как это выглядит в коде слоёв:

```java
@Repository
public class JpaOrderRepository implements OrderRepository {
    private final EntityManager em;

    public JpaOrderRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Order findById(long id) {
        return em.find(Order.class, id); // SQLException/PersistenceException → DataAccessException
    }
}

@Service
public class OrderService {
    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    public Order get(long id) {
        return orders.findById(id);
    }
}

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable long id) {
        return OrderDto.from(orders.get(id)); // тело ответа = JSON, не имя view
    }
}
```

`@Controller` без `@ResponseBody` обычно возвращает имя шаблона:

```java
@Controller
public class PageController {
    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("title", "Home");
        return "home"; // templates/home.html
    }
}
```

Можно ли `@Component` вместо `@Service`? Да, технически. Конвенция и будущая/существующая
инфраструктурная семантика (как у `@Repository`) — причина предпочитать правильный стереотип.

### 2.3. `@Bean` vs `@Component`

- `@Component` (+ сканирование): контейнер сам создаёт экземпляр класса (конструктор).
- `@Bean`: *ты* пишешь код создания — удобно для сторонних классов, нескольких экземпляров
  одного типа, условной сборки.

В `@Configuration` (полный режим, `proxyBeanMethods = true` по умолчанию) вызовы `@Bean`-методов
друг из друга идут через CGLIB-прокси конфигурации → singleton соблюдается. При
`proxyBeanMethods = false` (облегчённый режим, lite) это обычные вызовы Java → риск лишних
экземпляров, зато быстрее старт и проще для «плоских» конфигов.

### 2.4. Примеры `@Bean`

Фабричный метод в `@Configuration`: контейнер регистрирует *возвращаемое значение* как бин.
Имя бина по умолчанию — имя метода. Конфиг из
[BeanUsageDemo.java](code/BeanUsageDemo.java):

```java
@Configuration
static class AppConfig {

    // 1. сторонний / POJO без @Component
    @Bean
    Clock clock() {
        return new Clock();
    }

    @Bean
    Formatter formatter() {
        return new Formatter();
    }

    // 2. зависимости — параметры метода (DI фабрики)
    @Bean
    ReportService reportService(Clock clock, Formatter formatter) {
        return new ReportService(clock, formatter);
    }

    // 3. несколько бинов одного типа
    @Bean
    @Primary
    Greeter casualGreeter() {
        return new CasualGreeter();
    }

    @Bean
    Greeter formalGreeter() {
        return new FormalGreeter();
    }

    @Bean
    FormalMailer formalMailer(@Qualifier("formalGreeter") Greeter greeter) {
        return new FormalMailer(greeter);
    }

    // 4. initMethod / destroyMethod
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    ConnectionPool connectionPool() {
        return new ConnectionPool();
    }
}
```

**Вызов `@Bean` из `@Bean`** в полном `@Configuration` — через прокси конфигурации, тот же
singleton:

```java
@Bean
DataSource dataSource() {
    return new HikariDataSource(/* ... */);
}

@Bean
JdbcTemplate jdbcTemplate() {
    return new JdbcTemplate(dataSource()); // full mode → тот же DataSource
}
```

В lite-режиме (`proxyBeanMethods = false`) `dataSource()` — обычный Java-вызов → возможен
второй экземпляр.

### 2.5. Имя бина

По умолчанию: decapitalize имени класса / имя метода `@Bean`. Явно: `@Component("orderService")`,
`@Bean("orderService")`. Имя важно для `@Qualifier`, `Map<String, T>`, `@Resource`.

---

## 3. Жизненный цикл бина

Упрощение «create → DI → init → destroy» недостаточно для собеса. Ниже — и шаги **одного
бина**, и как они встраиваются в **поднятие контекста** (`ApplicationContext.refresh`).

### 3.1. Этапы поднятия `AnnotationConfigApplicationContext`

При `new AnnotationConfigApplicationContext(Config.class)` контейнер по сути делает:

1. Регистрация конфигурационных классов / сканирование → **BeanDefinition**.
2. **BeanFactoryPostProcessor** (в т.ч. `PropertySourcesPlaceholderConfigurer`) — правка
   метаданных *до* создания обычных бинов.
3. Создание инфраструктурных бинов: **BeanPostProcessor**, listener'ы. Их `@Bean` в
   `@Configuration` часто делают **`static`**, иначе цикл: конфиг не может быть полностью
   создан без BPP, а BPP объявлен в конфиге.
4. Создание остальных singleton'ов: для каждого — цепочка из §3.2.
5. Завершение `refresh` → публикация **`ContextRefreshedEvent`**. Здесь контекст уже
   «поднят»: все singleton созданы, прокси на месте. Удобная точка для отложенного старта
   (прогрев, вызов методов уже через прокси) — см. фазу 3 ниже.
6. В Boot после этого ещё `ApplicationReadyEvent` и старт встроенного сервера (§8).

Код старта и регистрации инфраструктуры — из
[ContextInitPhasesDemo.java](code/ContextInitPhasesDemo.java):

```java
public static void main(String[] args) {
    // здесь внутри: register → refresh → ContextRefreshedEvent
    AnnotationConfigApplicationContext ctx =
            new AnnotationConfigApplicationContext(Config.class);
    Quoter quoter = ctx.getBean(Quoter.class); // уже JDK-прокси
    quoter.sayQuote();
    ctx.close();
}

@Configuration
static class Config {

    // static @Bean — иначе цикл с ранней инициализацией @Configuration
    @Bean
    static BeanPostProcessor injectRandomIntBpp() {
        return new InjectRandomIntBpp();
    }

    @Bean
    static BeanPostProcessor profilingBpp() {
        return new ProfilingBpp();
    }

    @Bean
    AfterProxyInvoker afterProxyInvoker() {
        return new AfterProxyInvoker(); // слушает ContextRefreshedEvent
    }

    // return type = класс с @AfterProxy, не интерфейс Quoter
    @Bean
    TerminatorQuoter terminatorQuoter() {
        return new TerminatorQuoter("I'll be back");
    }
}
```

### 3.2. Цепочка одного singleton

1. Загрузка **BeanDefinition** (сканирование / `@Bean` / …).
2. **BeanFactoryPostProcessor** — правка *метаданных* до создания экземпляра.
3. Создание экземпляра (instantiation) — конструктор / factory-method. **Фаза 1:** полей
   от DI / кастомных BPP ещё нет.
4. Наполнение зависимостями (population / DI) — `@Autowired`, `@Value`, …
5. Обратные вызовы `*Aware` (`BeanNameAware`, `BeanFactoryAware`, `ApplicationContextAware`, …).
6. `BeanPostProcessor.postProcessBeforeInitialization` — кастомный inject, чтение аннотаций
   *до* возможного proxy.
7. Инициализация (**фаза 2**): `@PostConstruct` → `InitializingBean.afterPropertiesSet` →
   свой `initMethod`.
8. `BeanPostProcessor.postProcessAfterInitialization` — часто **оборачивание в прокси**.
9. Бин в контейнере; после полного refresh — **фаза 3** (`ContextRefreshedEvent`).
10. Уничтожение: `@PreDestroy` → `DisposableBean.destroy` → `destroyMethod`.

| Фаза | Когда | Что уже есть | Чего ещё нет |
|------|--------|--------------|--------------|
| 1. constructor | шаг 3 | объект | DI, `@Value`, кастомный BPP-inject |
| 2. `@PostConstruct` | шаг 7 | DI + BPP.before | внешний вызов через proxy (`this`) |
| 3. после `ContextRefreshed` | событие | proxy, весь граф singleton | — |

**Фазы 1–3 на одном бине** (`TerminatorQuoter` из демо):

```java
@Profiling
static class TerminatorQuoter implements Quoter {

    @InjectRandomInt(min = 2, max = 3)
    private int repeat;

    private final String message;

    TerminatorQuoter(String message) {
        this.message = message;
        // фаза 1: repeat ещё 0 — BPP.before не вызывался
        System.out.println("[фаза 1] constructor: repeat=" + repeat);
    }

    @PostConstruct
    void init() {
        // фаза 2: InjectRandomIntBpp уже заполнил repeat
        System.out.println("[фаза 2] @PostConstruct: repeat=" + repeat);
    }

    @AfterProxy
    @Override
    public void afterProxyPhase() {
        // фаза 3: вызов с ContextRefreshed через getBean → уже proxy
        System.out.println("[фаза 3] @AfterProxy, class=" + getClass().getName());
    }

    @Override
    public void sayQuote() {
        for (int i = 0; i < repeat; i++) {
            System.out.println("  quote: " + message);
        }
    }
}
```

**BPP.before** — кастомный inject до `@PostConstruct`:

```java
static final class InjectRandomIntBpp implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        for (Field field : bean.getClass().getDeclaredFields()) {
            InjectRandomInt ann = field.getAnnotation(InjectRandomInt.class);
            if (ann == null) continue;
            int value = ThreadLocalRandom.current().nextInt(ann.min(), ann.max() + 1);
            field.setAccessible(true);
            ReflectionUtils.setField(field, bean, value);
        }
        return bean;
    }
}
```

**BPP.after** — JDK-прокси (аннотацию `@Profiling` читаем в *before* и кладём в map):

```java
static final class ProfilingBpp implements BeanPostProcessor {
    private final Map<String, Class<?>> profilingBeans = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean.getClass().isAnnotationPresent(Profiling.class)) {
            profilingBeans.put(beanName, bean.getClass());
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = profilingBeans.get(beanName);
        if (targetClass == null) return bean;
        InvocationHandler handler = (proxy, method, args) -> {
            System.out.println("  [proxy] >>> " + method.getName());
            Object result = method.invoke(bean, args);
            System.out.println("  [proxy] <<< " + method.getName());
            return result;
        };
        return Proxy.newProxyInstance(
                targetClass.getClassLoader(), targetClass.getInterfaces(), handler);
    }
}
```

**Фаза 3** — listener на `ContextRefreshedEvent` (аннотации ищем на *исходном* классе,
вызов — через `getBean` → прокси):

```java
static final class AfterProxyInvoker implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    private ConfigurableListableBeanFactory factory;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        for (String name : factory.getBeanDefinitionNames()) {
            BeanDefinition bd = factory.getBeanDefinition(name);
            String className = resolveBeanClassName(bd); // см. ниже
            if (className == null) continue;
            Class<?> original = Class.forName(className);
            for (Method method : original.getMethods()) {
                if (!method.isAnnotationPresent(AfterProxy.class)) continue;
                Object bean = event.getApplicationContext().getBean(name);
                bean.getClass()
                        .getMethod(method.getName(), method.getParameterTypes())
                        .invoke(bean); // идёт через proxy
            }
        }
    }

    // у @Bean часто getBeanClassName() == null → берём return type factory-метода
    private static String resolveBeanClassName(BeanDefinition bd) {
        if (bd.getBeanClassName() != null) return bd.getBeanClassName();
        if (bd instanceof AnnotatedBeanDefinition annotated) {
            MethodMetadata metadata = annotated.getFactoryMethodMetadata();
            if (metadata != null) return metadata.getReturnTypeName();
        }
        return null;
    }
}
```

Порядок логов на практике (фрагмент вывода демо):

```text
[infra] static @Bean InjectRandomIntBpp
[infra] static @Bean ProfilingBpp
[factory] @Bean terminatorQuoter → new TerminatorQuoter
[фаза 1] constructor: repeat=0
[BPP.before] InjectRandomInt ...
[фаза 2] @PostConstruct: repeat=3
[BPP.after] создаю JDK-прокси ...
[ContextRefreshedEvent] ...
  [proxy] >>> afterProxyPhase
[фаза 3] @AfterProxy ...
```

Короткий порядок без кастомных аннотаций — [BeanLifecycleOrderDemo.java](code/BeanLifecycleOrderDemo.java):

```java
static final class DemoBean {
    DemoBean() { System.out.println("2. constructor"); }

    @PostConstruct
    void init() { System.out.println("4. @PostConstruct"); }

    @PreDestroy
    void destroy() { System.out.println("7. @PreDestroy"); }
}

@Bean
static BeanPostProcessor loggingBpp() {
    return new BeanPostProcessor() {
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof DemoBean)
                System.out.println("3. BPP.before");
            return bean;
        }
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof DemoBean)
                System.out.println("5. BPP.after — место для AOP-proxy");
            return bean;
        }
    };
}
```

Почему `@Transactional` из `@PostConstruct` того же класса молчит:

```java
@Component
public class InventoryService {
    @PostConstruct
    void init() {
        reserve("SKU-1"); // this.reserve → мимо прокси → транзакции нет
    }

    @Transactional
    public void reserve(String sku) { /* ... */ }
}
```

Обходы: другой бин; `ApplicationRunner`; `@Lazy` self. Полный файл фаз:
[ContextInitPhasesDemo.java](code/ContextInitPhasesDemo.java).

### 3.3. Циклические зависимости и `@PostConstruct`

Field/setter injection допускает цикл (early reference). В `@PostConstruct` сосед уже
*существует*, но его init может быть ещё не завершён. Код из
[CircularDependencyLifecycleDemo.java](code/CircularDependencyLifecycleDemo.java):

```java
static class Husband {
    @Autowired
    private Wife wife;
    private String money;

    @PostConstruct
    void init() {
        money = "5000$";
        // wife.getCompliment() здесь опасно: у Wife @PostConstruct может ещё не пройти
    }

    @RunAfterStart
    public void afterContextReady() {
        // оба уже проинициализированы
        System.out.println(wife.getCompliment().toUpperCase() + " > " + money);
    }
}

static class Wife {
    @Autowired
    private Husband husband;
    private String compliment;

    @PostConstruct
    void init() {
        compliment = "love";
    }

    @RunAfterStart
    public void afterContextReady() {
        System.out.println(husband.getMoney().toUpperCase() + " vs " + compliment);
    }
}
```

Отложенный вызов после refresh:

```java
static class RunAfterStartListener {
    @Autowired
    private ConfigurableListableBeanFactory factory;

    @EventListener
    public void onRefresh(ContextRefreshedEvent event) throws Exception {
        ApplicationContext context = event.getApplicationContext();
        for (String name : factory.getBeanDefinitionNames()) {
            // resolveClassName(bd) — как в AfterProxyInvoker
            Class<?> type = Class.forName(resolveClassName(factory.getBeanDefinition(name)));
            for (Method method : type.getMethods()) {
                if (!method.isAnnotationPresent(RunAfterStart.class)) continue;
                Object bean = context.getBean(name);
                bean.getClass()
                        .getMethod(method.getName(), method.getParameterTypes())
                        .invoke(bean);
            }
        }
    }
}
```

---

## 4. Области видимости (scopes)

| Область | Поведение |
|---------|-----------|
| `singleton` (по умолчанию) | один экземпляр на контейнер |
| `prototype` | новый экземпляр на каждый запрос у контейнера |
| `request` / `session` / `application` | веб-области |
| `websocket` | привязка к WebSocket-сессии |

Код из [SingletonPrototypePitfallDemo.java](code/SingletonPrototypePitfallDemo.java):

```java
@Configuration
static class Config {
    private int seq;

    @Bean
    @Scope("prototype")
    PrototypeToken token() {
        return new PrototypeToken(++seq);
    }

    @Bean BrokenClient brokenClient() { return new BrokenClient(); }
    @Bean FixedClient fixedClient() { return new FixedClient(); }
}

record PrototypeToken(int id) { }

// ловушка: singleton запомнил один prototype навсегда
static final class BrokenClient {
    @Autowired
    private PrototypeToken token;

    PrototypeToken use() { return token; } // call1 id=1, call2 id=1
}

// исправление: новый экземпляр на каждый getObject()
static final class FixedClient {
    @Autowired
    private ObjectProvider<PrototypeToken> tokens;

    PrototypeToken use() { return tokens.getObject(); } // call1 id=2, call2 id=3
}
```

Альтернативы: `@Lookup`, прокси области видимости
(`@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)`).

Prototype **не** получает полный обратный вызов уничтожения автоматически так же, как
singleton: контейнер не отслеживает все созданные prototype для остановки контекста.
Управление ресурсами — на вызывающем коде.

---

## 5. Разрешение зависимостей

При нескольких кандидатах одного типа:

1. **`@Primary`** — кандидат по умолчанию.
2. **`@Qualifier("beanName")`** / имя параметра (в ряде режимов) — явный выбор.
3. **`List<T>` / `Map<String, T>`** — все реализации (ключ map — имя бина).
4. **Условная регистрация** — `@Profile`, `@ConditionalOn*` — лишних кандидатов нет в контексте.
5. **Выбор во время выполнения** — отдельный `@Bean`-маршрутизатор / стратегия, а не `@Primary`
   «на удачу».

```java
public interface PaymentGateway {
    void pay(Money amount);
}

@Component
@Primary
public class CardGateway implements PaymentGateway { /* ... */ }

@Component
public class PaypalGateway implements PaymentGateway { /* ... */ }

@Service
public class CheckoutService {
    private final PaymentGateway defaultGateway;   // CardGateway (@Primary)
    private final PaymentGateway paypal;           // явно
    private final List<PaymentGateway> all;        // оба
    private final Map<String, PaymentGateway> byName; // ключи: cardGateway, paypalGateway

    public CheckoutService(
            PaymentGateway defaultGateway,
            @Qualifier("paypalGateway") PaymentGateway paypal,
            List<PaymentGateway> all,
            Map<String, PaymentGateway> byName) {
        this.defaultGateway = defaultGateway;
        this.paypal = paypal;
        this.all = all;
        this.byName = byName;
    }
}
```

Внедрение через конструктор + один кандидат → без лишних аннотаций. Несколько кандидатов без
`@Primary`/`@Qualifier` → `NoUniqueBeanDefinitionException` на старте (быстрый отказ при
старте, fail-fast — хорошо).

**Несколько `@Qualifier` на одной точке внедрения = AND.** Код из
[MultiQualifierListInjectionDemo.java](code/MultiQualifierListInjectionDemo.java):

```java
@Target({FIELD, METHOD, PARAMETER, TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Qualifier
@interface Comedy { }

@Target({FIELD, METHOD, PARAMETER, TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Qualifier
@interface Action { }

// composed: актёр сразу во всех жанрах
@Target({FIELD, METHOD, PARAMETER, TYPE})
@Retention(RUNTIME)
@Comedy @Action @Melodrama
@Qualifier
@interface AnyGenre { }

static final class Film {
    private final List<Actor> actors;

    Film(@Comedy @Action List<Actor> actors) { // AND: нужны оба qualifier
        this.actors = actors;
    }
}

@Configuration
static class Config {
    @Bean @Comedy Actor jim() { return () -> "Jim"; }
    @Bean @Action Actor tobey() { return () -> "Tobey"; }
    @Bean @Action @Comedy Actor stallone() { return () -> "Stallone"; }
    @Bean @Melodrama Actor julia() { return () -> "Julia"; }
    @Bean @AnyGenre Actor chuck() { return () -> "Chuck"; }
    @Bean Actor katy() { return () -> "Katy"; } // без qualifier — не в списке

    @Bean
    Film film(@Comedy @Action List<Actor> actors) {
        return new Film(actors); // → Stallone, Chuck
    }
}
```

---

## 6. AOP

**Сквозная функциональность (cross-cutting concern)** — журналирование, безопасность,
транзакции, метрики: нужна во многих местах, но не является сутью домена.

**Аспектно-ориентированное программирование (Aspect-Oriented Programming, AOP)** — способ
вынести сквозную функциональность в отдельные модули (аспекты), не размазывая её по
бизнес-методам.

Термины Spring AOP:

| Термин | Смысл |
|--------|--------|
| Аспект (Aspect) | модуль: срез (pointcut) + совет (advice) |
| Точка соединения (Join point) | в Spring AOP — вызов метода |
| Срез (Pointcut) | предикат «где» |
| Совет (Advice) | «что» (`@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around`) |
| Вплетение (Weaving) | у Spring AOP — во время выполнения через прокси |

### 6.1. Модель на прокси (proxy)

Spring AOP по умолчанию работает через **прокси (proxy)**:

- JDK dynamic proxy — если проксируется интерфейс;
- CGLIB — прокси-подкласс целевого класса.

С Spring 5.3+ / Boot 2.x+ прокси на основе класса часто используется шире (в т.ч. когда есть
интерфейс) — на собесе важнее *последствия* прокси, а не бренд библиотеки.

Ограничения:

- вызов `this.method()` внутри класса **не** идёт через прокси → advice / `@Transactional` молчат;
- `private` / `final` методы (и `final` класс для CGLIB) не перехватываются;
- перехват только вызовов, пришедших *через* бин из контейнера.

`@Around` обязан вернуть результат `proceed()` (или осознанную замену). Иначе «теряется»
возвращаемое значение целевого метода.

Альтернатива прокси — вплетение AspectJ (`AdviceMode.ASPECTJ`): самовызов (self-invocation)
тоже перехватывается, но цена — LTW / compile-time weaving и другая операционная модель. В
типичном Boot — режим PROXY.

Самовызов из [SelfInvocationAopDemo.java](code/SelfInvocationAopDemo.java):

```java
@Aspect
static class AuditAspect {
    @Around("execution(* ...OrderFacade.audited(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("  [ASPECT] before");
        Object result = pjp.proceed(); // обязан вернуть результат
        System.out.println("  [ASPECT] after");
        return result;
    }
}

static class OrderFacade {
    public void entryViaSelf(String id) {
        audited(id); // this.audited → ASPECT НЕ сработает
    }

    public void audited(String id) {
        System.out.println("  audited body, id=" + id);
    }
}

// снаружи: facade.audited("external") → ASPECT сработает
```

---

## 7. Транзакции

`@Transactional` — метаданные. Инфраструктура (`@EnableTransactionManagement` / автоконфигурация
Boot) регистрирует advisor → прокси → `TransactionInterceptor` → `PlatformTransactionManager`.

Рекомендация Spring: аннотировать **конкретный класс / методы класса**, не интерфейс (особенно
критично для режима AspectJ; для режима proxy интерфейс часто «работает», но хрупко).

### 7.1. Значения по умолчанию

- Распространение (propagation): `REQUIRED`
- Изоляция (isolation): `DEFAULT` (то, что у DataSource/БД)
- Откат (rollback): **только** `RuntimeException` и `Error`; проверяемое (checked) `Exception` —
  **commit**
- `readOnly = false`

### 7.2. Propagation — по отдельности

| Propagation | Идея |
|-------------|------|
| `REQUIRED` | войти в текущую или открыть новую (default) |
| `REQUIRES_NEW` | всегда новая; текущая suspend |
| `NESTED` | та же tx + savepoint (нужна поддержка TM/БД) |
| `SUPPORTS` | есть tx → участвовать; нет → без tx |
| `NOT_SUPPORTED` | suspend outer, работать вне tx |
| `MANDATORY` | tx обязательна, иначе `IllegalTransactionStateException` |
| `NEVER` | tx запрещена, иначе исключение |

**`REQUIRED`** — одна единица работы; inner и outer с одним commit. Откат inner → откат всей
outer.

```java
@Transactional // REQUIRED
public void placeOrder() {
    stock.reserve();   // тоже REQUIRED → та же tx
    orders.save(order);
}
```

**`REQUIRES_NEW`** — независимый commit (аудит / outbox). Два connection из pool; нет общей
атомарности с outer.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeAudit(String event) { /* ... */ }

@Transactional
public void placeOrder() {
    orders.save(order);
    audit.writeAudit("PAID"); // закоммитится даже при откате placeOrder
}
```

**`NESTED`** — rollback inner к savepoint, outer может продолжить. Не путать с `REQUIRES_NEW`:
падение outer откатит и nested-работу. В логах демо: `SAVEPOINT` → `RELEASE` → outer `COMMIT`.

**`SUPPORTS` / `NOT_SUPPORTED` / `MANDATORY` / `NEVER`** — см. вывод
[TransactionPropagationIsolationDemo.java](code/TransactionPropagationIsolationDemo.java):
`SUPPORTS` без outer → `active=false`; `NOT_SUPPORTED` → `SUSPEND` + работа вне tx;
`MANDATORY` без outer / `NEVER` с outer → `IllegalTransactionStateException`.

### 7.3. Isolation — по отдельности

Задаёт уровень для connection, когда tx **открывается**. Если метод только
**присоединяется** к существующей (`REQUIRED` join), isolation inner **обычно не меняет**
уже открытую tx — чтобы получить свой уровень, нужен `REQUIRES_NEW` (отдельная connection).

| Isolation | Практика |
|-----------|----------|
| `DEFAULT` | уровень БД / DataSource (часто `READ_COMMITTED`) |
| `READ_UNCOMMITTED` | почти не используют в OLTP |
| `READ_COMMITTED` | типичный default сервисов; нет dirty read |
| `REPEATABLE_READ` | стабильные повторные чтения в одной tx; phantom — зависит от СУБД |
| `SERIALIZABLE` | максимум защиты и блокировок / ретраев |

```java
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public Report loadDashboard() { /* ... */ }

@Transactional(isolation = Isolation.SERIALIZABLE)
public void transfer(Account from, Account to, Money amount) { /* ... */ }
```

Аномалии (dirty / non-repeatable / phantom) — свойство СУБД + isolation; Spring прокидывает
`Connection.setTransactionIsolation`. `readOnly = true` — hint (flush / routing), не замена
isolation.

### 7.4. Комбинации propagation + isolation

**Checkout + аудит** (частый кейс на собесе):

```java
@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public void checkout() {
    order.pay();
    audit.writeAudit("ORDER_PAID");
}

@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
public void writeAudit(String event) { /* ... */ }
```

В логах: outer `READ_COMMITTED` → `SUSPEND` → audit `REQUIRES_NEW`+`SERIALIZABLE` → `COMMIT` →
`RESUME` → outer `COMMIT`.

**Write + долгий read вне tx** (не держать write-connection):

```java
@Transactional
public void process() {
    updateState();
    report.heavyRead(); // NOT_SUPPORTED
}

@Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
public void heavyRead() { /* ... */ }
```

**Частичный откат строки в batch** — `NESTED` на `importOne` (или `REQUIRES_NEW`, но тяжелее
по connections и «уже закоммиченные» строки не откатятся с batch).

**Свой isolation на inner** — не полагаться на join `REQUIRED`; открывать
`REQUIRES_NEW` с нужным `isolation`.

Runnable со всеми сценариями и логами TM:
[TransactionPropagationIsolationDemo.java](code/TransactionPropagationIsolationDemo.java).

### 7.5. Почему «транзакция не работает»

1. Самовызов / `private` / `final`.
2. Метод вызван не через Spring-бин (ручной `new`, вызов из другого потока без передачи прокси).
3. Нет менеджера транзакций / неверный менеджер при нескольких DataSource.
4. Ожидали откат на checked exception без `rollbackFor`.
5. Вызов на стадии жизненного цикла до готовности прокси (см. раздел 3).

Обходы самовызова: вынести метод в другой бин (предпочтительно); внедрение себя через `@Lazy`;
`AopContext.currentProxy()` (требует `exposeProxy = true`, на проде редко любят).

Код из [TransactionalSelfInvocationDemo.java](code/TransactionalSelfInvocationDemo.java):

```java
static class PaymentService {
    @Autowired
    @Lazy
    private PaymentService self; // прокси самого себя

    void entryDirect() {
        pay("direct"); // this.pay → txActive=false
    }

    void entryViaSelf() {
        self.pay("via-self"); // через proxy → [TX] begin, txActive=true
    }

    @Transactional
    public void pay(String label) {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        System.out.println("  pay(" + label + ") txActive=" + active);
    }
}

@Configuration
@EnableTransactionManagement
static class Config {
    @Bean
    PlatformTransactionManager transactionManager() {
        return new LoggingTxManager(); // stub AbstractPlatformTransactionManager
    }

    @Bean
    PaymentService paymentService() {
        return new PaymentService();
    }
}
```

Связка с JPA: границы транзакции определяют, жив lazy-loading и когда выполняется flush;
подробности — в jpa-hibernate заметке.

### 7.6. `@Async` и `@Transactional`

Transaction sync — **ThreadLocal**. `@Async` уводит выполнение в **другой поток**, поэтому:

- tx вызывающего метода **не продолжается** в `@Async` (join через потоки невозможен);
- tx внутри async должна **открыться на worker-потоке**.

**Тот же метод** `@Async` + `@Transactional` — хрупко: зависит от порядка AOP-advice
(async должен быть «снаружи», tx — уже на executor). На проде лучше не смешивать на одном
методе.

**Надёжный паттерн** — разнести по бинам (код из
[AsyncTransactionalDemo.java](code/AsyncTransactionalDemo.java)):

```java
@Service
public class AsyncFacade {
    private final WorkService work;

    @Async
    public void processAsync(String label) {
        // worker-поток, tx caller'а здесь нет
        work.doInTx(label); // другой бин → proxy → BEGIN на этом же worker
    }
}

@Service
public class WorkService {
    @Transactional
    public void doInTx(String label) { /* ... */ }
}
```

В логах демо: `caller txActive=false` → `async-1 txActive=false` → `[TM] BEGIN on async-1` →
`doInTx txActive=true`.

**Не работает:**

```java
@Async
public void processAsyncBroken() {
    doInTxLocal(); // this. → @Transactional молчит, даже на worker
}

@Transactional
public void doInTxLocal() { /* ... */ }
```

**Семантика относительно caller:**

```java
@Transactional
public void placeOrder() {
    orders.save(order);      // tx A
    notifier.notifyAsync();  // @Async — возвращается сразу
} // tx A может закоммититься до старта notify
```

Для «после успешного commit» — `TransactionSynchronization.afterCommit()` / outbox, а не голый
`@Async` из середины tx.

**`afterCommit` кратко.** Колбэк Spring TX: регистрируешь через
`TransactionSynchronizationManager.registerSynchronization` внутри активной транзакции;
вызывается **только после успешного commit**, в том же потоке. Типичный сценарий — отложить
notify / `@Async` / событие до момента, когда данные уже в БД. На rollback не вызывается
(тогда сработает только `afterCompletion` с `STATUS_ROLLED_BACK`). Альтернатива на уровне
событий — `@TransactionalEventListener(phase = AFTER_COMMIT)`. Демо:
[AfterCommitSynchronizationDemo.java](code/AfterCommitSynchronizationDemo.java).

| Схема | Tx есть? |
|-------|----------|
| `@Async` → другой бин `@Transactional` | Да, на async-потоке |
| `@Async` + `this.@Transactional` | Нет (proxy) |
| Ожидание join к tx вызывающего | Нет (другой поток) |
| Оба на одном методе | Возможно, но хрупко по order advice |

---

## 8. Spring Framework vs Spring Boot

**Spring Framework** — ядро: IoC-контейнер, DI, AOP, транзакции, Spring MVC и смежные модули.
Ты сам собираешь стек и конфигурацию.

**Spring Boot** — слой *поверх* Framework: стартеры (starters), автоконфигурация
(auto-configuration), жёсткие умолчания (opinionated defaults), встроенный сервер (embedded
server), Actuator. Не заменяет Framework — упрощает его использование и поставку приложения.

| | Spring Framework | Spring Boot |
|--|------------------|-------------|
| Роль | библиотеки и контракты | способ быстро собрать приложение на этих библиотеках |
| Конфигурация | явная (`@Configuration`, XML, ручной wiring) | многое включается условиями (`@ConditionalOn*`) |
| Зависимости | модули подключаешь сам | `spring-boot-starter-*` + BOM версий |
| Запуск web | часто внешний Tomcat / свой `DispatcherServlet` | jar + embedded Tomcat/Jetty (типичный случай) |
| Без другого | полноценно работает без Boot | без Framework смысла нет |

Принципиально: Framework отвечает *как устроены бины, AOP, MVC*; Boot — *как это поднять с
минимумом шаблона и разумными умолчаниями*. `@SpringBootApplication` — не «другой Spring», а
композиция:

- `@SpringBootConfiguration` (`@Configuration`);
- `@EnableAutoConfiguration`;
- `@ComponentScan` (с exclude-фильтрами Boot).

На проде рассуждаешь категориями Framework (lifecycle, proxy, `@Transactional`); Boot ускоряет
связывание зависимостей (wiring) и поставку.

### 8.1. Запуск и автоконфигурация

Упрощённый путь старта (bootstrap):

1. Окружение / профили / свойства (Environment, profiles, properties).
2. Создание `ApplicationContext`, загрузка определений бинов (сканирование + автоконфигурация).
3. `refresh`: BeanFactoryPostProcessor → создание BPP → singleton'ы (жизненный цикл §3.2) →
   **`ContextRefreshedEvent`**.
4. В Boot: `ApplicationRunner` / `CommandLineRunner`, затем **`ApplicationReadyEvent`**.
5. Встроенный сервер (для web).

`ContextRefreshedEvent` ≠ `ApplicationReadyEvent`: первый — «IoC-граф готов»; второй — «приложение
готово обслуживать» (после runners, обычно перед/рядом с приёмом трафика). Для вызова через
proxy сразу после поднятия бинов достаточно refresh; для прогрева кэша в Boot часто берут
`ApplicationRunner`.

**Автоконфигурация (auto-configuration)** — набор `@Configuration`, которые включаются по
условиям (`@ConditionalOnClass`, `@ConditionalOnProperty`, `@ConditionalOnMissingBean`, …).
В Boot 3+/4 список — `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

Отладка: `debug=true` → Condition Evaluation Report. Переопределение: свой `@Bean` того же типа
часто выключает автоконфиг через `@ConditionalOnMissingBean`.

---

## 9. Кэш (Spring Cache)

**Spring Cache** — абстракция: аннотации задают *что* кэшировать, `CacheManager` + `Cache` —
*где* хранить. Механика та же, что у `@Transactional`: AOP-прокси + interceptor
(`CacheInterceptor`).

### 9.1. Включение

```java
@Configuration
@EnableCaching
static class Config {
    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("users", "orders");
        // прод: CaffeineCacheManager / RedisCacheManager / …
    }
}
```

В Boot часто starter (`caffeine` / `redis` / …) + `@EnableCaching` — менеджер подтянется
автоконфигом.

### 9.2. Аннотации

| Аннотация | Поведение |
|-----------|-----------|
| `@Cacheable` | hit → вернуть из кэша, метод не вызывать; miss → вызвать и положить |
| `@CachePut` | всегда вызвать метод и обновить кэш |
| `@CacheEvict` | удалить запись / весь cache (`allEntries = true`) |
| `@Caching` | несколько операций сразу |
| `@CacheConfig` | общие `cacheNames` на классе |

```java
static class UserService {
    @Cacheable(cacheNames = "users", key = "#id")
    public String findById(long id) {
        System.out.println("  load from DB id=" + id);
        return "user-" + id;
    }

    @CacheEvict(cacheNames = "users", key = "#id")
    public void evict(long id) {
        System.out.println("  evict id=" + id);
    }
}
```

### 9.3. Как отрабатывает вызов

1. Вызов идёт **через прокси** бина.
2. `CacheInterceptor` строит **ключ** (`KeyGenerator` / SpEL `key`).
3. `CacheManager.getCache(name).get(key)` — hit → return без target.
4. Miss → `proceed()` → `put(key, result)` (с учётом `unless` / `condition`).

Ключи:

- по умолчанию `SimpleKeyGenerator` — аргументы метода;
- явно: `key = "#id"`, `key = "#user.id"`, свой `KeyGenerator`;
- у объектов-ключей важны `equals` / `hashCode`.

`condition` — кэшировать ли *до* вызова; `unless` — *после* (часто
`unless = "#result == null"`).

### 9.4. Провайдеры

| Реализация | Когда |
|------------|--------|
| `ConcurrentMapCache` | локально, тесты, без TTL из коробки |
| Caffeine | in-process, TTL/size, один инстанс |
| Redis / Hazelcast | общий кэш между нодами |
| JCache (JSR-107) | через стандартный API |

Абстракция одна — смена бэкенда через другой `CacheManager`.

Подробнее про Redis, блокировки и выбор кэша — [redis.md](../redis/redis.md).

### 9.5. Как выбрать провайдер (кратко)

1. Один инстанс, справочники, микросекундная latency → **Caffeine** (локально).
2. Несколько нод, нужна общая картина и инвалидация → **Redis** (или Hazelcast).
3. Деньги / строгие инварианты → не кэшируй write-path без продуманного TTL и
   инвалидации; source of truth — БД.
4. Часто делают L1 (Caffeine) + L2 (Redis).

### 9.6. Ловушки

Self-invocation — как у транзакций. Код из
[CacheAbstractionDemo.java](code/CacheAbstractionDemo.java):

```java
static class UserService {
    @Autowired
    @Lazy
    private UserService self;

    @Cacheable(cacheNames = "users", key = "#id")
    public String findById(long id) {
        System.out.println("  load from DB id=" + id);
        return "user-" + id;
    }

    void loadViaSelf(long id) {
        findById(id); // this. → мимо кэша, каждый раз load
        findById(id);
    }

    void loadViaProxy(long id) {
        self.findById(id); // через proxy → второй вызов = hit
        self.findById(id);
    }
}
```

Другие:

- из `@PostConstruct` кэш-аспект часто ещё не готов → `ApplicationRunner` + вызов через proxy;
- кэш **результата метода**, не L2 Hibernate: апдейт в обход `@CacheEvict` / `@CachePut` →
  stale data;
- несколько инстансов + локальный Caffeine → расхождение → distributed cache / инвалидация;
- `sync = true` на `@Cacheable` — один поток грузит miss (stampede); поддержка зависит от
  менеджера;
- кэш `null` — смотри настройки / `unless`.

Spring Cache ≠ Hibernate second-level cache: разные слои (method cache vs persistence).

---

## 10. Spring Data

**Spring Data** — не ORM и не замена JPA. Это семейство модулей с общей моделью
**репозиториев**: объявляешь интерфейс → на старте Spring делает **JDK-прокси**, который
исполняет запросы к конкретному store.

| Слой | Ответственность |
|------|-----------------|
| Твой `XxxRepository` | контракт API |
| Spring Data Commons | прокси, разбор имён, пагинация, auditing, projections |
| Spring Data JPA / JDBC / Mongo… | store-specific исполнение |
| JPA / JDBC / драйвер Mongo… | реальный доступ к данным |

### 10.1. Как репозиторий становится бином

1. `@EnableJpaRepositories` / Boot auto-config сканирует пакеты на интерфейсы `Repository`.
2. На каждый интерфейс — `JpaRepositoryFactoryBean` (FactoryBean).
3. `RepositoryFactorySupport` поднимает JDK-прокси интерфейса.
4. Advice:
   - CRUD → `SimpleJpaRepository` (для JPA);
   - finder / `@Query` → `QueryExecutorMethodInterceptor` с картой `Method → RepositoryQuery`.

Запросы для finder-методов обычно **резолвятся на старте** (fail-fast при опечатке в имени).
Прокси — обычный Spring-бин (`@Autowired` в сервис).

```java
public interface DemoUserRepository extends JpaRepository<DemoUser, Long> {
    Optional<DemoUser> findByEmailAndActiveTrue(String email);

    @Query("select u from DemoUser u where lower(u.email) = lower(:email)")
    Optional<DemoUser> byEmailIgnoreCase(@Param("email") String email);
}
```

### 10.2. Вызов метода

```text
service.users.findByEmailAndActiveTrue("a@b.c")
        │
        ▼
 JDK-proxy (DemoUserRepository)
        │
        ├─ CrudRepository / JpaRepository
        │     → SimpleJpaRepository → EntityManager
        │
        └─ finder / @Query
              → QueryExecutorMethodInterceptor
                    → RepositoryQuery.execute(...)
                          → EntityManager / SQL
```

Дальше для JPA — persistence context, flush, dirty checking (зона Hibernate, не «магия Data»).

### 10.3. Query lookup

Стратегия `queryLookupStrategy` (default — `CREATE_IF_NOT_FOUND`):

| Стратегия | Поведение |
|-----------|-----------|
| `CREATE` | только derived из имени |
| `USE_DECLARED_QUERY` | только `@Query` / named; иначе ошибка старта |
| `CREATE_IF_NOT_FOUND` | сначала declared, иначе derived |

Порядок для finder (Data JPA, упрощённо): `@Query` / `@Procedure` → named query → derived из
имени.

### 10.4. Derived query

```java
List<User> findByEmailIgnoreCaseAndActiveTrue(String email);
```

1. Отрезается префикс (`find…By`, `count…By`, `exists…By`, `delete…By`, …).
2. Хвост → предикаты по свойствам entity (`address.city`, `And`/`Or`, `IgnoreCase`,
   `Between`, `True`/`False`, `OrderBy`…).
3. Модуль строит store-specific запрос.

Ограничения: длинные имена, слабая булева выразительность → `@Query` / `Specification` /
Querydsl. Свойство должно существовать в модели — иначе падение **на старте**.

### 10.5. `@Query`, `SimpleJpaRepository`, кастом

```java
@Query("select u from DemoUser u where u.active = true")
List<DemoUser> findActive();

@Query(value = "select * from users where …", nativeQuery = true)
List<DemoUser> nativeFind();
```

`SimpleJpaRepository` — обычный класс CRUD (не derived). На методах есть `@Transactional`
(часто `readOnly` на чтении): «голый» вызов репозитория без TX на сервисе всё равно откроет
границу метода репозитория. Для use-case из нескольких вызовов — **TX на service**.

Кастом рядом с proxy:

```java
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {}

public interface UserRepositoryCustom {
    List<User> search(UserFilter filter);
}

// суффикс Impl по умолчанию
public class UserRepositoryImpl implements UserRepositoryCustom {
    @PersistenceContext EntityManager em;
    public List<User> search(UserFilter filter) { /* Criteria */ }
}
```

### 10.6. Проекции, пагинация, модули

- Проекции (interface / DTO / constructor expression) — для read API вместо полного графа.
- `Page` (с count) vs `Slice` (без полного count); `Pageable` + `join fetch` коллекции —
  классическая ловушка (см. jpa-hibernate).
- **Data JPA** — PC, lazy, dirty checking. **Data JDBC** — другая модель, без JPA PC; это не
  «JPA через JDBC». Mongo/Redis/… — тот же стиль репозитория, другая физика store.

### 10.7. Картина и ловушки

```text
Controller → Service (@Transactional) → Repository (Data proxy)
                → SimpleJpaRepository / RepositoryQuery → EntityManager → Hibernate → JDBC
```

Типичные проблемы: бог-репозиторий из десятков derived; N+1 (Data не лечит); `save` ≠ UPSERT;
TX только на репозитории рвёт use-case; `@Modifying` без `clearAutomatically`; путаница
Data JDBC vs JPA.

Runnable: [SpringDataJpaRepositoryDemo.java](code/springdata/SpringDataJpaRepositoryDemo.java)
(+ [DemoUserRepository.java](code/springdata/DemoUserRepository.java)).  
Глубже про PC/TX — [jpa-hibernate.md](../databases/jpa-hibernate.md).

---

## 11. Spring Security

**Spring Security** — аутентификация и авторизация. В servlet-стеке ядро —
**`FilterChainProxy`**: один `Filter` в контейнере, внутри — упорядоченные
**`SecurityFilterChain`**. Опционально — method security на бинах через AOP.

### 11.1. Authentication vs Authorization

| | Authentication | Authorization |
|--|----------------|---------------|
| Вопрос | кто ты? | что тебе можно? |
| Результат | `Authentication` в `SecurityContext` | доступ к URL / методу / объекту |
| Ошибка | обычно 401 | обычно 403 |

### 11.2. Путь запроса

```text
HTTP → Servlet Container
  → FilterChainProxy
      → SecurityFilterChain (по securityMatcher)
          → SecurityContextHolderFilter
          → CorsFilter / CsrfFilter / …
          → auth-фильтры (form / Basic / Bearer …)
          → AnonymousAuthenticationFilter (если включено)
          → AuthorizationFilter
          → ExceptionTranslationFilter
      → DispatcherServlet → Controller
```

Фильтры Security — **раньше** MVC interceptor и controller advice.

### 11.3. `Authentication` и `SecurityContext`

```java
// principal, credentials, authorities, isAuthenticated()
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
```

- `SecurityContextHolder` по умолчанию — **ThreadLocal** (`MODE_THREADLOCAL`).
- В `@Async` / своём executor без пропагации context пустой →
  `MODE_INHERITABLETHREADLOCAL` или явная передача.
- Session-приложение: context кладётся в `HttpSession` между запросами.
- Stateless JWT: каждый запрос заново строит `Authentication` из token.

### 11.4. Аутентификация (username/password)

```text
Credentials → Authentication (unauthenticated)
  → AuthenticationManager.authenticate
      → AuthenticationProvider (напр. DaoAuthenticationProvider)
          → UserDetailsService.loadUserByUsername
          → PasswordEncoder.matches
      → authenticated Authentication + authorities
  → SecurityContextHolder.setAuthentication(...)
```

`UserDetailsService` только загружает пользователя; пароль сверяет encoder (BCrypt и т.д.).
`DelegatingPasswordEncoder` хранит id алгоритма в hash (`{bcrypt}…`).

### 11.5. Конфигурация Boot 3+ / Security 6+

`WebSecurityConfigurerAdapter` нет. Идиоматика — бин `SecurityFilterChain`.
Код из [SecurityFilterChainDemo.java](code/security/SecurityFilterChainDemo.java):

```java
@Configuration
@EnableMethodSecurity
static class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // осознанно: httpBasic API без cookie-session
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public").permitAll()
                .requestMatchers("/api/admin").hasRole("ADMIN") // authority ROLE_ADMIN
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
```

Несколько `@Bean SecurityFilterChain` — разные зоны (`securityMatcher("/api/**")` vs
`/admin/**`). Без matcher — конфликт/непредсказуемость.

Boot без своего chain: default «всё закрыто» + form login + пароль в логе.

### 11.6. URL-авторизация vs method security

| Уровень | Инструмент |
|---------|------------|
| HTTP | `authorizeHttpRequests` / `AuthorizationFilter` |
| Метод | `@PreAuthorize` / `@PostAuthorize` / `@PreFilter` (AOP) |
| Данные | фильтрация в запросе / `@PostFilter` |

```java
@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
public Order get(long id) { … }
```

`permitAll` на URL **не отменяет** `@PreAuthorize`. Self-invocation (`this.`) обходит method
security — как у `@Transactional`.

`hasRole("ADMIN")` → authority **`ROLE_ADMIN`**.  
`hasAuthority("ADMIN")` → буквально `ADMIN`.

### 11.7. Session vs JWT / CSRF / CORS

| | Session (form) | Bearer JWT (API) |
|--|----------------|------------------|
| Где состояние | сервер (session) | клиент (token) |
| CSRF | обычно нужен | часто off при чистом Bearer |
| Инвалидация | invalidate session | short TTL / denylist / refresh |

CSRF: при cookie-session браузер сам шлёт cookie → нужен token. При чистом
`Authorization: Bearer` автоматом не шлётся → CSRF обычно отключают вместе со
`STATELESS`. Антипаттерн: `csrf.disable()` + cookie form login «потому что JSON».

CORS preflight (`OPTIONS`) должен пройти до жёсткой auth, иначе UI «не логинится».

### 11.8. JWT resource server (схема)

```text
Authorization: Bearer <jwt>
  → BearerTokenAuthenticationFilter
      → JwtDecoder (подпись, exp, issuer)
      → JwtAuthenticationConverter → Authentication
  → AuthorizationFilter
```

Resource server проверяет token; Authorization server выдаёт. Это разные роли.

### 11.9. Ловушки

1. Несколько chain без `securityMatcher`.
2. Role prefix: в токене `ADMIN`, в правиле `hasRole('ADMIN')` → 403.
3. CSRF off + cookie session.
4. `SecurityContext` пуст в `@Async`.
5. Self-invocation ↔ `@PreAuthorize`.
6. Забыли `permitAll` на `/error` → циклы при ошибках.
7. Actuator открыт без отдельной политики.

Положение в стеке:

```text
Security filters → DispatcherServlet → Controller
  → Service (@PreAuthorize, @Transactional) → Data / …
```

---

## 12. Web MVC: краткий каркас

`DispatcherServlet` — фронт-контроллер (Front Controller): принимает HTTP, выбирает обработчик
(handler) (`@RequestMapping` и семейство), вызывает контроллер, обрабатывает возвращаемое
значение / исключения, рендерит представление или пишет тело.

- `@Controller` — обычно имя представления + модель.
- `@RestController` — сериализация тела (Jackson и др.).
- `ResponseEntity<T>` — когда нужны статус, заголовки, тело явно.
- Только `@ResponseBody` / RestController без `ResponseEntity` — статус по умолчанию (часто 200),
  ошибки — через resolvers исключений / `@ControllerAdvice`.

Слои перехвата (разные уровни стека):

| Механизм | Уровень |
|----------|---------|
| Servlet `Filter` | до/вокруг DispatcherServlet |
| `HandlerInterceptor` | Spring MVC, вокруг обработчика |
| AOP / `@Transactional` | вызовы Spring-бинов |

Статика в Boot: `classpath:/static`, `/public`, `/resources`, `/META-INF/resources`
(порядок и кастомизация — через `spring.web.resources`).

---

## 13. Профили и условия

- `@Profile("dev")` — бин/конфиг активен только при профиле. Иначе —
  `NoSuchBeanDefinitionException` у потребителей.
- `@ConditionalOnProperty` / `@ConditionalOnClass` / … — точечное включение (особенно в
  библиотеках и автоконфигурации).
- Активация: `spring.profiles.active`, переменные окружения, командная строка.

Не путать: профиль режет *набор бинов*; свойство может выбирать *какого* кандидата создать.

---

## 14. REST и SOAP

| | REST (часто HTTP + JSON) | SOAP |
|--|--------------------------|------|
| Стиль | ресурсы и глаголы HTTP | RPC-операции в XML-конверте |
| Контракт | OpenAPI / договорённости | WSDL + XSD |
| Где встречается | публичные API, микросервисы | legacy / enterprise-интеграции |

Пример REST:

```http
POST /orders
Content-Type: application/json

{"userId": 1, "items": […]}
```

```http
GET /orders/42
→ 200 {"id":42,"status":"PAID"}
```

SOAP — XML-сообщение с `Envelope` / `Body` и операцией вроде `PlaceOrder`. Строже
контракт и tooling, тяжелее читать и дебажить.

«RESTful» на собесе: осмысленные ресурсы, правильные методы и коды, идемпотентность
`GET`/`PUT`/`DELETE`. HATEOAS на практике встречается редко.

---

## 15. HTTP cookie

**Cookie** — маленький кусок данных, который сервер просит браузер хранить и присылать
назад.

```http
Set-Cookie: SESSION=abc; Path=/; HttpOnly; Secure; SameSite=Lax
```

Браузер потом сам добавит:

```http
Cookie: SESSION=abc
```

Важные флаги:

| Флаг | Зачем |
|------|--------|
| `HttpOnly` | JS не читает cookie → сложнее украсть через XSS |
| `Secure` | только HTTPS |
| `SameSite` | ограничить отправку с чужих сайтов (CSRF) |
| `Path` / `Domain` | куда cookie относится |

Два мира сессий:

- **cookie-session** — сервер знает сессию, браузер шлёт cookie → нужен CSRF-token;
- **Bearer JWT** — клиент кладёт токен в заголовок сам → CSRF обычно не про cookie.

Антипаттерн: выключить CSRF и оставить form-login на cookie «потому что у нас JSON API».

---

## 16. События в Spring

Внутри одного приложения:

```java
@Service
class OrderService {
    private final ApplicationEventPublisher events;

    @Transactional
    public void pay(long orderId) {
        // … меняем БД …
        events.publishEvent(new OrderPaidEvent(orderId));
    }
}

@Component
class MailListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPaid(OrderPaidEvent e) {
        mail.send(e.orderId());
    }
}
```

`AFTER_COMMIT` — письмо только если транзакция реально зафиксировалась.

События **между сервисами** (Kafka и саги) — в
[microservices.md](../microservices/microservices.md).

---

## 17. Spring Cloud Config

**Config Server** — общее хранилище настроек (часто Git). Сервисы при старте забирают
`application.yml` для своего имени и профиля.

```text
Git repo
  application.yml
  order-service-prod.yml
        ↓
  Config Server
        ↓
  order-service (client)
```

В Boot 2.4+ типично:

```properties
spring.config.import=optional:configserver:http://config:8888
spring.application.name=order-service
```

Плюсы: один источник правды, смена конфига без пересборки образа (с оговорками).
Минусы: зависимость на старте, секреты в Git лучше не хранить открытым текстом
(Vault / encrypted).

Если уже Kubernetes ConfigMap/Secrets + GitOps — отдельный Config Server часто не нужен.

---

## 18. OpenFeign

**OpenFeign** — декларативный HTTP-клиент: пишешь интерфейс, получаешь proxy.

```java
@FeignClient(name = "payment-service", url = "${payment.url}")
public interface PaymentClient {
    @PostMapping("/charges")
    ChargeResponse charge(@RequestBody ChargeRequest request);
}
```

Вызов `paymentClient.charge(...)` → HTTP POST.

Рядом со Spring Cloud: load balancer, timeouts, circuit breaker (Resilience4j).

На что смотрят:

- таймауты и ретраи только на идемпотентные методы;
- понятный error decoder;
- Feign — синхронный RPI, не замена очереди.

Альтернативы: `RestClient` / `WebClient`, Spring Interface Clients.

---

## 19. WebSockets

Обычный HTTP: спросил — ответил — соединение закрыли (упрощённо).
**WebSocket** — соединение живёт, данные идут в обе стороны, когда нужно.

```text
Клиент ←→ сервер: «цена акции изменилась» без постоянного polling
```

В Spring: raw WebSocket или STOMP (часто + SockJS для fallback).

Когда брать: чаты, котировки, live-доски. Когда нет: редкие запросы — хватит REST или SSE.

На нескольких нодах: sticky session или общий Redis pub/sub, иначе сообщение попало
на ноду A, а сокет висит на ноде B.

---

## 20. Spring Integration

Фреймворк для **интеграционных потоков** (EIP): Message, Channel, Transformer, Router,
Gateway, адаптеры к Kafka/JMS/файлам/HTTP/mail.

```text
файл → channel → transform JSON → router → Kafka / БД
```

Бери, когда в одном приложении много протоколов и маршрутов сообщений.
Для «просто слушать Kafka» чаще хватает Spring Kafka. Не путать со
**Spring Cloud Stream** (абстракция binder'ов).

---

## 21. Spring Data REST

Экспортирует Spring Data-репозиторий как HTTP API почти без контроллера.

```java
@RepositoryRestResource
public interface UserRepository extends JpaRepository<User, Long> { }
```

→ `GET /users`, `POST /users`, … (HAL).

Удобно для прототипа. На проде часто рано упираются в: утечка модели БД наружу,
тонкий контроль над DTO/security/валидацией. Тогда — явные `@RestController`.

---

## 22. Actuator в Docker / K8s

**Actuator** — технические endpoint'ы: health, metrics, info, иногда env/heapdump.

В контейнере / Kubernetes:

```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
```

Имеет смысл разделять:

- **liveness** — процесс жив (не зависит от падения БД навсегда);
- **readiness** — можно слать трафик (БД/брокер доступны).

Безопасность: не открывай `/actuator/env` и dump'ы в интернет. Отдельный management
port или жёсткие правила в Spring Security.

---

## 23. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [ContextInitPhasesDemo.java](code/ContextInitPhasesDemo.java) | этапы refresh: фаза 1/2/3, static BPP, JDK-proxy, `ContextRefreshedEvent` |
| [BeanLifecycleOrderDemo.java](code/BeanLifecycleOrderDemo.java) | порядок constructor → BPP → `@PostConstruct` → after → destroy |
| [CircularDependencyLifecycleDemo.java](code/CircularDependencyLifecycleDemo.java) | цикл DI + отложенный старт после refresh |
| [BeanUsageDemo.java](code/BeanUsageDemo.java) | сценарии `@Bean`: зависимости, `@Primary`/`@Qualifier`, init/destroy |
| [MultiQualifierListInjectionDemo.java](code/MultiQualifierListInjectionDemo.java) | несколько `@Qualifier` на `List` = AND |
| [CacheAbstractionDemo.java](code/CacheAbstractionDemo.java) | `@Cacheable` / `@CacheEvict`, self-invocation vs `@Lazy` self |
| [springdata/SpringDataJpaRepositoryDemo.java](code/springdata/SpringDataJpaRepositoryDemo.java) | Data JPA: proxy-репозиторий, derived + `@Query` (H2) |
| [security/SecurityFilterChainDemo.java](code/security/SecurityFilterChainDemo.java) | `SecurityFilterChain`, httpBasic, 401/403, roles |
| [SelfInvocationAopDemo.java](code/SelfInvocationAopDemo.java) | advice срабатывает снаружи прокси и молчит на `this.` |
| [SingletonPrototypePitfallDemo.java](code/SingletonPrototypePitfallDemo.java) | singleton «замораживает» prototype; `ObjectProvider` чинит |
| [TransactionalSelfInvocationDemo.java](code/TransactionalSelfInvocationDemo.java) | `@Transactional` + самовызов vs `@Lazy` self |
| [TransactionPropagationIsolationDemo.java](code/TransactionPropagationIsolationDemo.java) | `propagation` / `isolation` по отдельности и в комбинациях |
| [AsyncTransactionalDemo.java](code/AsyncTransactionalDemo.java) | `@Async` + `@Transactional`: worker-поток, anti-pattern self |
| [AfterCommitSynchronizationDemo.java](code/AfterCommitSynchronizationDemo.java) | `TransactionSynchronization.afterCommit` vs rollback |

---

## 24. Вопросы для собеседования и ответы

### 1. Чем `BeanFactory` отличается от `ApplicationContext`?

`BeanFactory` — минимальный IoC: getBean, области видимости, основы жизненного цикла.
`ApplicationContext` расширяет его: публикация событий, i18n (`MessageSource`), доступ к
ресурсам, удобная интеграция с AOP и корпоративными возможностями. В Boot и почти во всех
серверных приложениях используют `ApplicationContext`. `BeanFactory` всплывает, когда
объясняют иерархию контейнеров или пишут очень лёгкий runtime.

### 2. На каком шаге жизненного цикла появляется transactional-прокси и почему это ломает вызов из `@PostConstruct`?

Прокси обычно создаёт `BeanPostProcessor` в `postProcessAfterInitialization`, *после*
`@PostConstruct`. Даже если снаружи уже виден проксированный бин, вызов `this.txMethod()` из
init того же экземпляра идёт мимо прокси. Плюс для части инфраструктур (кэш) момент
`@PostConstruct` слишком ранний. Надёжнее: отдельный бин, `ApplicationRunner`, либо осознанный
self-прокси после полного поднятия.

### 3. Почему `@Transactional` на `private` методе не работает в режиме proxy?

Прокси перехватывает *внешние* вызовы к переопределяемым методам. `private` не входит в
контракт subclass / JDK proxy. Метод вызывается напрямую на целевом объекте. Решение: `public`
(или package/protected при class proxy) + вызов через другой бин / прокси.

### 4. `REQUIRED` внутри `REQUIRED` vs `REQUIRES_NEW`: что с откатом?

`REQUIRED` присоединяется к существующей транзакции — одна единица работы, общий откат.
`REQUIRES_NEW` приостанавливает текущую и открывает независимую: commit/rollback внутренней не
равен автоматически откату внешней (и наоборот). Типичный кейс `REQUIRES_NEW` — аудит/outbox,
который должен сохраниться даже при откате бизнес-транзакции (с осторожностью к
согласованности). Демо: [TransactionPropagationIsolationDemo.java](code/TransactionPropagationIsolationDemo.java).

### 4a. Чем `NESTED` отличается от `REQUIRES_NEW`?

`NESTED` — savepoint в той же физической tx (нужна поддержка TM/БД): откат inner не обязан
ронять outer, но откат outer затронет и nested. `REQUIRES_NEW` — отдельная tx и обычно другой
connection; commit inner окончателен относительно outer. Не взаимозаменяемы.

### 4b. Почему `isolation` на inner с `REQUIRED` «не сработал»?

При join к существующей tx Spring/TM не переоткрывает connection с новым isolation. Аннотация
inner фактически не меняет уровень уже идущей tx. Нужен `REQUIRES_NEW` (или открывать нужный
isolation на outer).

### 5. Зачем `rollbackFor = Exception.class`, если «ошибка и так откатит»?

Не откатит: по умолчанию — только непроверяемые (unchecked). Checked exception (в т.ч.
завёрнутый бизнес-сигнал) приводит к **commit**, если не настроить `rollbackFor` / не бросить
runtime. На собесе часто ловят ожидание «любое исключение = rollback».

### 6. Чем `@Repository` отличается от `@Component` кроме названия?

Для классов под `@Repository` Spring подключает трансляцию исключений: исключения,
специфичные для провайдера persistence, превращаются в единообразную иерархию
`DataAccessException`. Это реальная инфраструктурная семантика, не только «красивое имя слоя».

### 7. Как корректно внедрить prototype в singleton?

Не обычным полем-зависимостью. Варианты: `ObjectProvider<T>` / `Provider<T>`, метод `@Lookup`,
прокси области видимости. Иначе singleton держит один prototype на весь срок жизни.

### 8. Полный `@Configuration` vs `proxyBeanMethods = false`?

Полный режим: CGLIB-прокси на конфигурации, меж-`@Bean` вызовы дедуплицируют singleton.
Облегчённый: быстрее, меньше магии, но `@Bean` внутри другого `@Bean` — обычный Java-вызов. Для
простых конфигов и старта — lite; когда фабричные методы вызывают друг друга и важна
идентичность экземпляров — full.

### 9. Filter vs HandlerInterceptor vs Aspect — куда что класть?

- Filter — уровень servlet: CORS preflight, цепочка фильтров безопасности, обёртка
  request/response.
- Interceptor — MVC: доступ к `HandlerMethod`, модели, pre/post handle в диспетчере.
- Aspect — любые Spring-бины, не только web: транзакции, аудит сервисов, метрики use-case.

Класть аутентификацию в Aspect «на сервис» можно, но Security Filter Chain / method security —
стандарт экосистемы; не дублировать без причины.

### 10. Как выбрать реализацию по свойству в Boot 3/4 без `@Resource(name="${...}")`?

Предпочтительнее: `@ConditionalOnProperty` на каждой реализации / конфигурации; или один
`@Bean`-метод, читающий `@Value` / `@ConfigurationProperties` и возвращающий нужную
реализацию; или `Map<String, Port>` + явный ключ из properties. `@Resource` с плейсхолдером —
устаревший стиль, хрупче к именам и IDE/анализу.

### 11. Что такое внедрение самого себя (self-injection) и когда оно оправдано?

Бин внедряет свой же прокси (`@Lazy` на себя), чтобы вызвать метод с `@Transactional` /
`@Async` / `@Cacheable` через прокси. Оправдано как тактический фикс. Стратегически лучше
разрезать класс: публичный фасад и отдельный бин с аннотированными методами — проще
тестировать и читать.

### 12. Нужен ли внешний Web Container (Tomcat «снаружи») для Spring Boot web-приложения?

Нет для типичного jar: Boot поднимает встроенный сервер. Внешний servlet container нужен для
war-развёртывания классической модели. Spring IoC-контейнер при этом живёт *внутри* процесса и
не равен «Web Container» Java EE. EJB/Web контейнеры — часть Java EE сервера; Spring может
работать и без них.

### 13. Почему `@Around` «съел» возвращаемое значение?

Advice не вернул результат `proceed()` (или вернул `null` / значение, несовместимое с void).
Для `@Around` контракт: вы — полный перехватчик потока, включая возврат и исключения.

### 14. Чем сканирование компонентов отличается от `@Component`?

`@Component` помечает класс кандидатом. Сканирование говорит контейнеру, в каких пакетах этих
кандидатов искать и регистрировать. Одно без другого (при отсутствии `@Bean`/`@Import`) не
создаёт бин.

### 15. Как `@Cacheable` связан с теми же ловушками, что и `@Transactional`?

Тот же proxy-AOP: самовызов и ранний жизненный цикл ломают ожидания. Дополнительно абстракция
кэша может быть не готова в `@PostConstruct`. Для прогрева кэша — `ApplicationRunner` и вызов
через прокси / другой бин.

### 16. Чем «фаза 3» после `ContextRefreshedEvent` отличается от `@PostConstruct`?

`@PostConstruct` — init конкретного бина: зависимости уже внедрены, но (а) прокси этого бина
с точки зрения `this` ещё не используется, (б) другие singleton'ы могут быть ещё не созданы /
не проинициализированы. `ContextRefreshedEvent` — после того как контейнер закончил refresh:
типичные singleton на месте, BPP.after уже отработали, `getBean` отдаёт proxy. Туда кладут
прогрев и логику, которой нужен целый граф или гарантированный вызов через proxy.

Демо: [ContextInitPhasesDemo.java](code/ContextInitPhasesDemo.java).

### 17. Почему у `@Bean` listener не видит аннотации на классе через `getBeanClassName()`?

Для бинов из factory-метода Spring часто оставляет `beanClassName == null` до/без явной
записи: тип результата известен из сигнатуры метода, а не как `beanClass` XML-бина. Нужно
читать `FactoryMethodMetadata.getReturnTypeName()` и объявлять return type как класс, на
котором висят искомые аннотации (не «широкий» интерфейс без них).

### 18. Чем принципиально отличается Spring Framework от Spring Boot?

Framework — ядро (IoC, AOP, MVC, транзакции): без него нет Spring-приложения. Boot — слой
поверх: стартеры, автоконфигурация, умолчания, embedded-сервер. Можно писать на Framework без
Boot; Boot без Framework не существует. `@SpringBootApplication` не отменяет модель бинов —
включает scan + auto-config поверх того же контейнера.

### 19. Чем `@Cacheable` отличается от `@CachePut`?

`@Cacheable` при hit метод не вызывает. `@CachePut` всегда вызывает метод и кладёт результат
в кэш (удобно после create/update). Путаница на собесе: «оба кэшируют» — да, но семантика
вызова разная.

### 20. Почему Spring Cache и Hibernate L2 cache — разные вещи?

Spring Cache — method-level абстракция вокруг вызова сервиса/репозитория (AOP). L2 — кэш
сущностей/коллекций внутри persistence provider. Можно использовать оба; инвалидация и
consistency у каждого свои. Обновление сущности «мимо» сервиса с `@Cacheable` оставляет stale
в Spring Cache, даже если L2 согласован.

### 21. Локальный Caffeine vs Redis для Spring Cache?

Caffeine: низкая latency, простота, данные только в процессе → при нескольких инстансах
рассинхрон. Redis: общий кэш, сеть/сериализация, отдельный failure domain. Выбор — про
consistency между нодами и операционную модель, не про «кто быстрее на одном JVM».

### 22. Чем Spring Data отличается от JPA / Hibernate?

JPA — спека persistence; Hibernate — провайдер. Spring Data — слой репозиториев-прокси поверх
store (в т.ч. JPA). Data не заменяет ORM: без EntityManager/Hibernate Data JPA не работает.
Data JDBC / Mongo — другие модули с похожим API репозитория.

### 23. Как Spring превращает `findByEmail` в запрос?

На старте `QueryLookupStrategy` (обычно `CREATE_IF_NOT_FOUND`): нет `@Query`/named → парсинг
имени → store-specific `RepositoryQuery`. Вызов идёт в `QueryExecutorMethodInterceptor` →
execute. Ошибка в имени свойства — fail при подъёме контекста.

### 24. Зачем `@Transactional` на `SimpleJpaRepository`, если TX на сервисе?

CRUD-методы базовой реализации уже транзакционны (часто readOnly на find). Это страховка для
прямых вызовов репозитория. Use-case из нескольких обращений к БД всё равно нужно оборачивать
TX на service, иначе границы и flush рвутся между вызовами.

### 25. Чем Spring Data JDBC отличается от Spring Data JPA?

JPA: persistence context, lazy, dirty checking, merge/persist. JDBC-модуль: без этой модели,
другой подход к агрегатам и `save`. Это не «тонкая обёртка JPA над DataSource».

### 26. Как устроен Spring Security в веб-приложении?

`FilterChainProxy` → один или несколько `SecurityFilterChain` → фильтры (context, CSRF/CORS,
auth, `AuthorizationFilter`, exception translation) → затем MVC. `Authentication` живёт в
`SecurityContext` (`SecurityContextHolder`). URL-правила и `@PreAuthorize` — разные уровни.

### 27. Чем 401 отличается от 403 в Spring Security?

401 — нет (или не принята) аутентификация (`AuthenticationException` → entry point).  
403 — аутентифицирован, но нет прав (`AccessDeniedException`). Обрабатывает
`ExceptionTranslationFilter`.

### 28. `hasRole('ADMIN')` vs `hasAuthority('ADMIN')`?

`hasRole` ищет authority с префиксом `ROLE_` → `ROLE_ADMIN`. `hasAuthority` — точное совпадение
строки. Типичный баг: в JWT claim `ADMIN`, в правиле `hasRole('ADMIN')` → 403.

### 29. Когда отключать CSRF?

Оправдано для stateless API с Bearer (браузер сам не приложит token). Опасно отключать при
cookie-session / form login. CSRF и CORS — разные проблемы.

### 30. Почему `@PreAuthorize` не сработал при вызове из того же класса?

Method security — AOP-proxy. `this.method()` обходит proxy — как `@Transactional` /
`@Cacheable`. Нужен вызов через другой бин / self-inject / разнести методы.

### 31. Сработает ли `@Transactional`, если метод помечен `@Async`?

Tx вызывающего **не join’ится** (другой поток, ThreadLocal). Надёжно: `@Async`-фасад вызывает
**другой бин** с `@Transactional` — tx открывается на worker. На одном методе оба annotation —
хрупко из‑за порядка advice. `this.@Transactional` внутри `@Async` — не сработает (proxy).
Для работы строго после commit outer — `afterCommit` / outbox, не голый `@Async` из tx.
Демо: [AsyncTransactionalDemo.java](code/AsyncTransactionalDemo.java).

### 32. Зачем Spring Cloud Config, если есть env-переменные?

Централизованные профильные настройки для многих сервисов (часто из Git) и иногда refresh
без пересборки. Env/K8s Secrets хорошо закрывают секреты и простые значения; Config Server —
когда много общих yaml и единый источник. См. [§17](#17-spring-cloud-config).

### 33. Чем Feign отличается от RestClient?

Feign — декларативный интерфейс → HTTP. RestClient/WebClient — императивный вызов.
Идея одна (синхронный/реактивный RPI); Feign удобен как «клиент = API-контракт».

### 34. Cookie-session или JWT?

Cookie-session: сервер хранит сессию, удобный logout, нужен CSRF. JWT Bearer: клиент
носит token, проще горизонтально масштабировать resource server, сложнее мгновенная
инвалидация. См. [§15](#15-http-cookie).

### 35. Liveness и readiness в Actuator — в чём разница?

Liveness: «процесс пора перезапустить». Readiness: «пока не готовы принимать трафик».
Готовый health с падением БД на liveness может устроить рестарт-луп — часто БД только в
readiness. См. [§22](#22-actuator-в-docker--k8s).
