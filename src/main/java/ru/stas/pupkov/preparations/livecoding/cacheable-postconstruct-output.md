# Что выведется: `@Cacheable` + `@PostConstruct` (self-invocation)

## Условие

```java
@EnableCaching
@SpringBootApplication
public class TestApplication {
    private int counter;

    @Cacheable("counter")
    public Integer scoring() { return counter++; }

    @CacheEvict("counter")
    public void clearCache() {}

    @PostConstruct
    public void init() {
        System.out.println("scoring: " + scoring());
        System.out.println("scoring: " + scoring());
        clearCache();
        System.out.println("scoring: " + scoring());
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
```

## Ответ

```
scoring: 0
scoring: 1
scoring: 2
```

## Почему

Spring Cache по умолчанию работает через **AOP-прокси** (JDK/CGLIB). Аннотации срабатывают только когда метод вызван **через прокси**.

В `init()` вызовы идут как `this.scoring()` / `this.clearCache()`:

1. `@PostConstruct` контейнер вызывает на целевом бине (не через cache-proxy);
2. даже внутри бина это **self-invocation** → advice `@Cacheable` / `@CacheEvict` **не применяется**.

Итог: каждый `scoring()` реально выполняет `return counter++`:

| Вызов | возвращает | `counter` после |
|-------|------------|-----------------|
| 1-й   | 0          | 1               |
| 2-й   | 1          | 2               |
| 3-й   | 2          | 3               |

`clearCache()` пустой и тоже без прокси — на кэш не влияет (кэша по сути и нет на этом пути).

## Если бы кэш работал (вызов через proxy)

Было бы `0`, `0`, `1`: два hit’а с закэшированным `0`, после evict — снова miss с `1`.  
В этом коде — **не** тот случай.

## Как заставить кэш работать

- вынести `scoring`/`clearCache` в другой бин и инжектить его;
- или self-injection (`@Lazy` прокси самого себя) и звать `self.scoring()`;
- AspectJ mode (`@EnableCaching(mode = AdviceMode.ASPECTJ)`) — реже на практике.

См. также: [CacheAbstractionDemo.java](../spring/code/CacheAbstractionDemo.java).

## Демо

- [CacheablePostConstructOutputDemo.java](code/CacheablePostConstructOutputDemo.java) — тот же сценарий через `AnnotationConfigApplicationContext`.

## Вопросы для собеседования и ответы

**Почему `@EnableCaching` «не помог»?**  
Кэш включён в контексте, но advice не перехватывает внутренние вызовы. Инфраструктура есть — точки среза нет.

**`@PostConstruct` вызывается до или после создания прокси?**  
BeanPostProcessors создают прокси; `@PostConstruct` обрабатывается на целевом экземпляре. В любом случае вызов `scoring()` из `init` — не через cache advisor.

**Сработала бы аннотация на `init`, если сделать `scoring` через `ApplicationContext.getBean`?**  
Да, если получить proxy-бин и вызвать метод на нём — кэш применится.
