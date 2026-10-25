# JDBC, JPA и Hibernate

Java 17+, Jakarta Persistence 3.x / Hibernate 6.x, Spring Boot 3.x.
Заметка для изучения и подготовки к собеседованиям.
Транзакции, изоляция и SQL-семантика — в [databases.md](databases.md) и [sql.md](sql.md);
здесь — доступ из приложения: JDBC, спецификация ORM и механика Hibernate.

## Содержание

1. [Слои доступа к данным](#1-слои-доступа-к-данным)
2. [JDBC](#2-jdbc)
3. [JPA: entity и mapping](#3-jpa-entity-и-mapping)
4. [Persistence context и lifecycle](#4-persistence-context-и-lifecycle)
5. [Ассоциации, cascade, owning side](#5-ассоциации-cascade-owning-side)
6. [Fetch, N+1, OSIV](#6-fetch-n1-osiv)
7. [Flush, dirty checking, generators](#7-flush-dirty-checking-generators)
8. [Блокировки и `@Version`](#8-блокировки-и-version)
9. [`equals` / `hashCode` у entity](#9-equals--hashcode-у-entity)
10. [Кэши: L1 и L2](#10-кэши-l1-и-l2)
11. [Spring: `@Transactional` и Spring Data JPA](#11-spring-transactional-и-spring-data-jpa)
12. [Когда JDBC, когда JPA](#12-когда-jdbc-когда-jpa)
13. [Демо-код](#13-демо-код)
14. [Вопросы для собеседования и ответы](#14-вопросы-для-собеседования-и-ответы)

---

## 1. Слои доступа к данным

| Слой | Что это | Типичный API |
|------|---------|--------------|
| JDBC (Java Database Connectivity) | стандарт вызова SQL из Java через драйвер | `DataSource`, `Connection`, `PreparedStatement` |
| JPA (Jakarta Persistence API) | спецификация объектно-реляционного отображения (Object-Relational Mapping, ORM) | `EntityManager`, annotations, JPQL |
| Hibernate | реализация JPA + собственный Session API и расширения | Session, `@BatchSize`, L2 cache, statistics |

Точная формулировка: **JPA — контракт; Hibernate — движок**, который его реализует и часто
выходит за пределы спеки. Spring Data JPA — ещё один слой: репозитории-прокси поверх
EntityManager, не замена пониманию persistence context (контекста персистентности).

ORM не отменяет SQL: он даёт unit of work (единицу работы), identity map и генерацию SQL.
На проде SQL всё равно читают в логах и планах.

---

## 2. JDBC

### 2.1. Состав

- **JDBC API** — `java.sql`, `javax.sql` (в Jakarta EE-стеке имена пакетов для persistence
  другие; JDBC остаётся `javax.sql` / `java.sql`).
- **JDBC-драйвер** — vendor-код, регистрируется через `ServiceLoader` (JDBC 4+);
  явный `Class.forName` для драйвера — legacy.

JDBC URL: `jdbc:<subprotocol>:<subname>`, например
`jdbc:postgresql://localhost:5432/app`.

### 2.2. Ключевые типы

| Тип | Роль |
|-----|------|
| `DataSource` | фабрика соединений; норма для pool / Spring |
| `DriverManager` | простой lookup по URL; для скриптов и тестов |
| `Connection` | сессия с БД + граница транзакции |
| `PreparedStatement` | SQL с bind-параметрами |
| `CallableStatement` | stored procedures / functions |
| `ResultSet` | курсор/результат выборки |
| `DatabaseMetaData` / `ResultSetMetaData` | метаданные |

В Spring Boot pool по умолчанию — HikariCP. `DriverManager` без pool на OLTP — антипаттерн.

### 2.3. Lifecycle

1. Взять `Connection` из pool.
2. При явной TX: `setAutoCommit(false)`.
3. Создать `PreparedStatement`, выставить параметры.
4. `executeQuery` / `executeUpdate` / batch.
5. `commit` или `rollback`.
6. Закрыть Statement/ResultSet/Connection (`try-with-resources`; connection возвращается в pool).

Потерянный `rollback` + connection ушёл в pool с открытой TX / `autoCommit=false` —
типичный инцидент: «чужие» запросы видят чужое состояние или получают странные ошибки.

### 2.4. `Statement` vs `PreparedStatement`

**Проблема:** если пользовательский ввод вшивать в SQL конкатенацией строк, данные
становятся частью **текста** запроса и могут изменить его грамматику (SQL injection).
Тот же класс ошибок — хрупкий ручной escape и «динамический» native SQL без bind.

```text
login = alice' OR '1'='1
→ WHERE login = 'alice' OR '1'='1' AND ...
```

**Верхнеуровнево:**

| | `Statement` | `PreparedStatement` |
|--|-------------|---------------------|
| SQL | каждый раз целиком строкой | шаблон с `?` + отдельно значения |
| Роль данных | легко смешать с синтаксисом | bind: значение ≠ фрагмент SQL |
| Где уместен | статический SQL без внешнего ввода | почти весь application SQL |

`PreparedStatement` решает главную задачу: **отделить значения от синтаксиса** запроса.
Повторное выполнение одного шаблона и batch (`addBatch` / `executeBatch`) — удобный
следствие. Кэш/server-side планы на стороне СУБД — бонус драйвера, не главная причина
выбора; клиентский `prepareStatement` не всегда равен server-side prepare.

Имя таблицы/колонки через `?` передать нельзя (placeholder только для значений) —
тогда whitelist идентификаторов, не склейка «что пришло от клиента».

Демо: [JdbcPreparedStatementSemanticsDemo.java](code/JdbcPreparedStatementSemanticsDemo.java).

### 2.5. Транзакции и isolation в JDBC

Граница TX — `Connection`. Уровень изоляции — `Connection.setTransactionIsolation(...)`.
Смысл уровней и аномалий — в [databases.md](databases.md). ORM и Spring выставляют
isolation на том же соединении.

### 2.6. Практические нюансы

- **Типы:** деньги — `BigDecimal`; `getInt` + `wasNull` для nullable; timezone у
  `Timestamp` vs `OffsetDateTime` — частый источник «сдвига на N часов».
- **Batch:** `addBatch` / `executeBatch` для bulk; размер батча согласуют с СУБД и памятью.
- **Fetch size / streaming:** большие выборки без материализации всего `ResultSet` в heap;
  поведение курсора зависит от драйвера и СУБД (например, PostgreSQL и `autoCommit`).
- **Pool exhaustion:** долгая TX + внешний I/O внутри TX держит connection занятым.

---

## 3. JPA: entity и mapping

**Entity** — класс, экземпляры которого JPA умеет сопоставлять со строками таблицы
(`@Entity`, `@Id`, поля/ассоциации).

Базовый каркас:

```java
@Entity
@Table(name = "product")
class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Version
    private long version;
}
```

Важные элементы mapping:

| Конструкция | Смысл |
|-------------|--------|
| `@Embedded` / `@Embeddable` | value-object в тех же колонках |
| `@ElementCollection` | коллекция базовых/embeddable; отдельная таблица; lifecycle с родителем |
| `@Enumerated(STRING\|ORDINAL)` | `STRING` безопаснее при эволюции enum |
| Inheritance (`SINGLE_TABLE` / `JOINED` / `TABLE_PER_CLASS`) | trade-off: nullability vs JOIN vs полиморфные запросы |
| AttributeConverter | маппинг доменного типа ↔ колонка |

JPQL оперирует **именами entity и полей**, не таблиц (пока не native query).
Criteria API — programmatic-построение; удобен для динамических фильтров, verbose.

### 3.1. `@Embeddable` и `@Embedded`

Простыми словами: **кусок полей без своей таблицы и без своего id**.

Адрес сам по себе не «строка в БД заказов» — он лежит **в колонках таблицы заказа**.

```java
@Embeddable
public class Address {
    private String city;
    private String street;
}

@Entity
public class Order {
    @Id
    private Long id;

    @Embedded
    private Address deliveryAddress;
}
```

Таблица `orders` получит колонки вроде `city`, `street` (имена можно переопределить).

| | `@Entity` | `@Embeddable` |
|--|-----------|---------------|
| Своя таблица | да | нет (колонки у владельца) |
| Свой `@Id` | да | нет |
| Lifecycle | сам / через связи | живёт и умирает с родителем |

Два одинаковых embed в одной entity — через `@AttributeOverride`:

```java
@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "city", column = @Column(name = "bill_city")),
    @AttributeOverride(name = "street", column = @Column(name = "bill_street"))
})
private Address billing;
```

`@ElementCollection` списка адресов → уже **отдельная** таблица коллекций, не «всё в
orders».

Демо: [EmbeddableMappingDemo.java](code/EmbeddableMappingDemo.java).

---

## 4. Persistence context и lifecycle

**Persistence context (PC)** — набор managed entity + отложенные SQL-операции в рамках
unit of work (обычно транзакция / lifecycle `EntityManager`).

### 4.1. Состояния

| Состояние | Смысл |
|-----------|--------|
| Transient / New | новый объект, PC не знает |
| Managed / Persistent | в PC; участвует в dirty checking |
| Detached | был managed; PC закрыт/`clear`; изменения сами не пишутся |
| Removed | помечен на `DELETE` при flush |

```text
new Product("x")   → TRANSIENT
em.persist(p)      → MANAGED   (INSERT часто отложен до flush)
p.setName("y")     → dirty; UPDATE на flush без явного save
em.clear() / end   → DETACHED
em.remove(p)       → REMOVED
```

### 4.2. Identity map

В одном PC один id → один instance Java:

```java
Product a = em.find(Product.class, 1L);
Product b = em.find(Product.class, 1L);
assert a == b; // без второго SELECT
```

Это контракт согласованности unit of work, не «просто кэш».

### 4.3. Операции EntityManager

| Метод | Поведение |
|-------|-----------|
| `persist` | сделать transient → managed; schedule INSERT |
| `find` | загрузка / hit PC |
| `getReference` | proxy по id; SELECT при доступе к состоянию (с оговорками) |
| `merge` | скопировать состояние detached/new в managed instance; **вернуть** managed |
| `remove` | schedule DELETE |
| `flush` | синхронизация PC → SQL |
| `clear` / `detach` | вытолкнуть из PC |

Типичный паттерн внутри TX:

```java
@Transactional
public void rename(Long id, String name) {
    Product p = em.find(Product.class, id); // MANAGED
    p.setName(name);                        // merge не нужен
}
```

`merge` нужен, когда объект пришёл **извне** (API/DTO→entity с id) и не является managed:

```java
Product detached = mapper.toEntity(dto);
Product managed = em.merge(detached); // дальше работать с managed
```

Ошибка: вызвать `merge(detached)` и продолжить мутировать `detached` — правите не тот instance.

Демо: [EntityLifecycleDemo.java](code/EntityLifecycleDemo.java).

---

## 5. Ассоциации, cascade, owning side

### 5.1. Виды связей

`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`.
В БД `@ManyToMany` — join table; часто заменяют явной entity связи (атрибуты на ребре,
проще lifecycle).

### 5.2. Owning vs inverse

**Owning side** пишет FK (или строки join table). Inverse (`mappedBy`) — только зеркало
в объектной модели.

```java
@Entity
class Author {
    @OneToMany(mappedBy = "author") // inverse
    List<Book> books = new ArrayList<>();
}

@Entity
class Book {
    @ManyToOne                      // owning → колонка author_id
    Author author;
}
```

Если сделать только `author.getBooks().add(book)` и не выставить `book.setAuthor(author)`,
в памяти связь есть, в БД FK останется `NULL`.

Доменный helper синхронизирует обе стороны:

```java
void addBook(Book book) {
    books.add(book);
    book.setAuthor(this);
}
```

Демо: [AssociationOwningSideDemo.java](code/AssociationOwningSideDemo.java).

### 5.3. Cascade и orphanRemoval

`CascadeType.ALL` + `orphanRemoval = true` уместны, когда коллекция — часть **агрегата**
(lifecycle ребёнка совпадает с родителем: строки заказа).

На shared reference (`Order → User`) cascade ALL / orphanRemoval опасны: можно удалить
чужую сущность из БД.

#### За что отвечает `orphanRemoval`

`orphanRemoval = true` на связи (чаще `@OneToMany` / `@OneToOne`): если ребёнок
**отвязан от родителя** (убрали из коллекции / обнулили ссылку), JPA должен сделать
`DELETE` строки в БД — ребёнок стал «сиротой» относительно агрегата и не остаётся в таблице.

```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderLine> lines = new ArrayList<>();

order.getLines().remove(line); // после flush/commit → DELETE order_line
```

Без `orphanRemoval` то же `remove` из списка — не «магия удаления»: часто только
разрыв связи (FK → `NULL` или только изменение в памяти — зависит от owning side).
Это сильнее, чем «убрать из `List`» в Java: политика **удаления строки**.

#### `orphanRemoval` vs `CascadeType.REMOVE`

| | `orphanRemoval` | `CascadeType.REMOVE` |
|--|-----------------|----------------------|
| Когда | ребёнка **убрали** из коллекции/ссылки родителя | удалили **родителя** (`em.remove(order)`) |
| Смысл | «выпал из агрегата → DELETE» | «удалили корень → каскадом DELETE детей» |

Часто ставят вместе для агрегата: дети живут только внутри родителя.

#### Практика

- Уместно: `Order` → `OrderLine`, компоненты агрегата без самостоятельной жизни.
- Не уместно: `Order` → `User` / `Product` (shared) — `remove` из коллекции удалит
  пользователя/товар.
- Нужна корректная bidirectional-синхронизация (owning side с FK), иначе провайдер может
  не увидеть orphan.
- SQL уходит на **flush** persistence context, не мгновенно в момент `list.remove`.

Демо: [OrphanRemovalDemo.java](code/OrphanRemovalDemo.java).

Fetch type по умолчанию (JPA): коллекции — `LAZY`; `@ManyToOne` / `@OneToOne` — `EAGER`.
В практике `@ManyToOne` часто делают `LAZY` явно.

---

## 6. Fetch, N+1, OSIV

### 6.1. N+1

Один SELECT родителей + N SELECT при доступе к lazy-коллекции в цикле.

| Стратегия | Число SQL (N родителей) |
|-----------|-------------------------|
| LAZY + цикл | 1 + N |
| `join fetch` / entity graph | ~1 (с оговорками) |
| `@BatchSize` / `IN (...)` | 1 + ceil(N / batch) |

```java
// плохо
orders.forEach(o -> o.getItems().size());

// под use-case
@Query("select o from Order o join fetch o.items where o.created >= :from")
List<Order> findWithItems(Instant from);
```

Демо: [NPlusOneFetchDemo.java](code/NPlusOneFetchDemo.java).

`EAGER` на коллекциях почти всегда вреден: любой `find` тащит граф, часто транзитивно.

### 6.2. EntityGraph

**Проблема:** ассоциации обычно `LAZY`; нужный граф («заказ + позиции + клиент») надо
явно подтянуть под use-case. Иначе N+1, `LazyInitializationException` или вредный
глобальный `EAGER` на mapping’е.

**EntityGraph** (JPA 2.1+) — fetch plan для конкретной операции (`find` / query): «для
этого вызова считай эти атрибуты eager», без смены `fetch` на entity навсегда.

```text
Запрос корня (Order)
    + graph: items, customer
        → провайдер грузит перечисленные ассоциации
        → остальные — по типу graph (FETCH / LOAD)
```

#### Named graph и применение

```java
@Entity
@NamedEntityGraph(
    name = "Order.withItems",
    attributeNodes = {
        @NamedAttributeNode("items"),
        @NamedAttributeNode(value = "customer", subgraph = "customer.details")
    },
    subgraphs = @NamedSubgraph(
        name = "customer.details",
        attributeNodes = @NamedAttributeNode("addresses")
    )
)
class Order { ... }

EntityGraph<?> graph = em.getEntityGraph("Order.withItems");
Order order = em.find(Order.class, id,
    Map.of("jakarta.persistence.fetchgraph", graph));
// query.setHint("jakarta.persistence.fetchgraph", graph);
```

#### `FETCH` vs `LOAD`

| Тип hint | Узлы **в** graph | Узлы **вне** graph |
|----------|------------------|---------------------|
| **fetchgraph** (`FETCH`) | как `EAGER` | как `LAZY` (игнорируют mapping EAGER) |
| **loadgraph** (`LOAD`) | как `EAGER` | как в mapping (`LAZY`/`EAGER`) |

`FETCH` жёстче режет граф. `LOAD` — «обязательно догрузи перечисленное, остальное как
обычно». В Spring Data у `@EntityGraph` default — **`FETCH`**.

#### Spring Data JPA

```java
@EntityGraph(value = "Order.withItems", type = EntityGraphType.LOAD)
Optional<Order> findById(Long id);

@EntityGraph(attributePaths = { "items", "customer.addresses" })
List<Order> findByStatus(OrderStatus status);
```

Если заданы `attributePaths`, имя named graph игнорируется — динамический graph.
Разные методы репозитория = разные планы; mapping остаётся `LAZY`.

#### vs `join fetch`

| | `join fetch` | EntityGraph |
|--|--------------|-------------|
| Где | текст JPQL | hint / `@EntityGraph` / metadata |
| Переиспользование | копипаста запроса | named graph на нескольких query |
| Derived query | неудобно | `@EntityGraph` на `findBy…` |
| Контроль SQL-shape | явный в JPQL | стратегия у провайдера |

Оба бьют в одну цель. Graph удобнее при многих методах; `join fetch` — когда нужен
полный контроль над запросом.

#### Ловушки

- Две коллекции в одном graph → cartesian product / `MultipleBagFetchException` (как у
  двойного collection `join fetch`). Часто: один collection fetch + `@BatchSize` /
  второй запрос / DTO.
- Graph не заменяет проекции: для read API часто лучше DTO, чем entity-граф.
- Hint на **этот** запрос; уже managed в PC под новый graph сами не перезагрузятся.
- Пагинация + collection в graph — те же грабли, что у [§6.5](#65-пагинация--join-fetch-коллекции).

Практика: mapping `LAZY` → graph / `join fetch` / DTO **внутри** сервиса под use-case.

### 6.3. LazyInitializationException

Доступ к lazy вне активного PC/TX → исключение (если нет OSIV и proxy не инициализирован).
Лечение: fetch/graph/DTO **внутри** сервиса, а не «открыть Session пошире».

### 6.4. Open Session In View (OSIV)

Session/EM живёт на весь HTTP request. Jackson может трогать lazy-поля «успешно», но:

- connection дольше занимает pool;
- N+1 прячется в сериализации;
- границы загрузки размазаны по view.

В Spring Boot OSIV исторически включён по умолчанию.

### 6.5. Пагинация + `join fetch` коллекции

Хочется одним запросом: страницу родителей (`ORDER BY` + `LIMIT`/`OFFSET`) и их
коллекции без N+1 (`join fetch`). На практике это конфликтует.

```java
@Query("select o from Order o join fetch o.items order by o.created desc")
Page<Order> page(Pageable pageable);
```

SQL по смыслу такой:

```sql
SELECT o.*, i.*
FROM orders o
JOIN order_item i ON i.order_id = o.id
ORDER BY o.created DESC
LIMIT 10 OFFSET 0;  -- режет строки JOIN, не «10 заказов»
```

#### Почему ломается

JOIN размножает строку родителя по числу детей. Пример: order 1 → 3 items, order 2 → 1,
order 3 → 2 → в результате 6 строк. `LIMIT 2` может вернуть только две строки первого
заказа → Hibernate соберёт **один** `Order` с обрезанной коллекцией; второго заказа на
«странице из 2» не будет.

Последствия:

- меньше родителей, чем просили в `page size`;
- у попавших коллекция может быть неполной;
- `Page.totalElements` / count по тому же join считают строки join, а не distinct order.

Hibernate: `hibernate.query.fail_on_pagination_over_collection_fetch=true` — лучше упасть,
чем тихо отдать кривую страницу.

`select distinct o … join fetch` убирает дубли entity **после** загрузки; SQL `LIMIT`
часто всё равно режет размноженный join. `distinct` ≠ «LIMIT по уникальным order_id».

Важна именно **collection** fetch (`@OneToMany` / `@ManyToMany`). `join fetch` to-one
(`o.customer`) родителя по числу детей так не размножает — с пагинацией обычно совместим.

#### Обходы

1. **Два запроса** (типичный прод-паттерн):
   - `SELECT id FROM orders WHERE … ORDER BY … LIMIT/OFFSET` — страница id;
   - `SELECT o FROM Order o JOIN FETCH o.items WHERE o.id IN :ids ORDER BY …` — граф;
   - count — отдельный, **без** fetch коллекции.
2. Page/slice **без** collection fetch → items вторым запросом (`IN` / `@BatchSize`).
3. Окно / subquery на SQL (native, JOOQ, Blaze-Persistence) — сначала id страницы, потом join.

В Spring Data `Pageable` + `@Query` с `join fetch` коллекции — классическая ловушка:
на данных «у каждого ровно 1 child» может «случайно работать», на реальных — нет.

Демо: [PaginationJoinFetchDemo.java](code/PaginationJoinFetchDemo.java).

---

## 7. Flush, dirty checking, generators

### 7.1. Dirty checking

Hibernate сравнивает snapshot managed entity с текущим состоянием и генерирует UPDATE
на flush. Явный `save` для уже managed не обязателен (Session API `save`/`update` —
legacy относительно JPA `persist`/`merge`).

### 7.2. FlushMode

При `FlushMode.AUTO` провайдер может сделать flush **перед JPQL**, если query должен
увидеть незаписанные изменения PC. Это consistency, не сюрприз «магии».

Для bulk-job иногда `FlushMode.COMMIT` / ручной flush + периодический `clear()`,
чтобы не раздувать PC.

### 7.3. Generators и JDBC batch

| Strategy | Поведение | Batch INSERT |
|----------|-----------|--------------|
| `IDENTITY` | id после INSERT | часто плохо батчится |
| `SEQUENCE` + `allocationSize` | id заранее из sequence | дружит с `hibernate.jdbc.batch_size` |
| UUID | без sequence | ок, хуже locality B-tree PK |

`allocationSize` в `@SequenceGenerator` **обязан** совпадать с `INCREMENT` sequence в DDL.
Иначе — коллизии id, «дыры», падения на уникальности.

Полезные настройки Hibernate (имена зависят от версии/Boot):
`hibernate.jdbc.batch_size`, `order_inserts`, `order_updates`, `@BatchSize` на коллекциях.

Для миллионов строк — `StatelessSession`, native bulk, `JdbcTemplate` / COPY, а не
гигантский PC с dirty checking.

---

## 8. Блокировки и `@Version`

### 8.1. Optimistic locking

```java
@Version
private long version;
```

SQL вида:

```sql
UPDATE account SET balance = ?, version = 1
WHERE id = ? AND version = 0
```

0 updated rows → `OptimisticLockException`. Защищает lost update на уровне строки при
конкурирующих read-modify-write. Retry — **бизнес-осознанный**: перечитать, заново
проверить инварианты, повторить операцию.

Демо: [OptimisticLockVersionDemo.java](code/OptimisticLockVersionDemo.java).

### 8.2. Pessimistic locking

`LockModeType.PESSIMISTIC_WRITE` → `SELECT … FOR UPDATE` (диалект СУБД).
Уместно при высокой вероятности конфликта и короткой критической секции.
Держать lock на время внешнего HTTP — антипаттерн (pool + latency + deadlock risk).

`@Transactional` сам по себе lost update не лечит — см. [databases.md](databases.md).

---

## 9. `equals` / `hashCode` у entity

Антипаттерн: `equals`/`hashCode` только по `@GeneratedValue` id.

- до persist `id == null` → все entity часто в одном hash-bucket;
- после присвоения id `hashCode` меняется → объект «теряется» в `HashSet`
  (в т.ч. в коллекциях ассоциаций).

Устойчивее: immutable business key (`sku`, `(tenantId, code)`), либо не помещать
transient entity в hash-based структуры до стабильного ключа.
Сравнение только по id после assign — осознанный компромисс с дисциплиной использования.

Демо: [EntityEqualsHashCodePitfallDemo.java](code/EntityEqualsHashCodePitfallDemo.java).

Proxy Hibernate: `instanceof` / `getClass()` vs `Hibernate.getClass` / pattern matching —
учитывать при equals по типу.

---

## 10. Кэши: L1 и L2

```text
find(id)
  → L1 (persistence context)     — всегда, один Session/EM
  → L2 (second-level cache)      — опционально, shared между Session
  → БД
```

Рядом, но отдельно: **query cache** — кэш результатов запросов (обычно список id).

| Кэш | Область | Что хранит |
|-----|---------|------------|
| L1 / First level (PC) | один EntityManager / Session | Java instances (managed) |
| L2 / Second level | shared across sessions (нужен provider) | disassembled state по id |
| Query cache | shared | id'шники результата запроса |

### 10.1. L1 — persistence context

Обязательная часть unit of work, не «выключаемый Redis внутри Hibernate»:

- живёт, пока жив EM/Session (часто = `@Transactional`);
- identity map: один id → один instance в этом PC;
- dirty checking, cascade, flush.

```java
Product a = em.find(Product.class, 1L); // SELECT или hit L1
Product b = em.find(Product.class, 1L); // без SQL, a == b
```

| Свойство | Смысл |
|----------|--------|
| Область | только этот Session |
| Согласованность | сильная в TX (видны свои незакоммиченные изменения) |
| После commit / `clear` / close | L1 пуст; объекты detached |
| Выключить | нельзя — без L1 нет нормального JPA UoW |

Огромные выборки в одной TX раздувают L1 → память и долгий flush; пачками `clear` или
`StatelessSession` / JDBC.

См. также [§4](#4-persistence-context-и-lifecycle) и [EntityLifecycleDemo.java](code/EntityLifecycleDemo.java).

### 10.2. L2 — second-level cache

Опциональный кэш **между** Session (JVM / кластер через Ehcache, Caffeine, Redis-регион…).

- Хранит не те же Java-объекты, а **состояние** entity по id; при hit гидрируется
  новый managed instance в **текущий** L1.
- Нужны конфигурация + обычно `@Cacheable` / регион на entity.

```java
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Entity
class Country { ... } // типичный кандидат — справочник
```

| Strategy (идея) | Когда |
|-----------------|--------|
| `READ_ONLY` | почти не меняется |
| `NONSTRICT_READ_WRITE` | редкие апдейты, допустим краткий stale |
| `READ_WRITE` | обычные обновления |
| `TRANSACTIONAL` | редко; нужен подходящий provider / JTA |

Точная семантика зависит от cache provider. После UPDATE регион обычно инвалидируется
или обновляется; при ошибках конфигурации / нескольких узлах без distributed cache —
риск stale.

**Уместно:** read-mostly справочники, частый `find` по id, измеримый hit-rate.  
**Вредно:** горячие агрегаты (`Order`, балансы); «L2 на все entity»; тяжёлый collection
cache без нужды; несколько инстансов приложения с локальным L2 без общей инвалидации.

### 10.3. Query cache

```text
JPQL → query cache: список id → каждый id через L2 (или БД)
```

Без L2 обычно бесполезен: кэшируются id, entity всё равно грузятся из БД. Инвалидация
грубая (завязана на обновления таблиц) → часто низкий hit-rate.

### 10.4. L1 vs L2

| | L1 | L2 |
|--|----|----|
| Включён | всегда | опционально |
| Область | один Session | shared |
| Содержимое | Java instances | данные по id |
| Роль | UoW, identity, dirty checking | ускорить чтения across TX |
| Согласованность | сильная в TX | по стратегии региона |

Фраза на собес: L1 — обязательный PC per-session; L2 — опциональный shared cache entity
по id для read-mostly; query cache без L2 почти бессмысленен.

Демо: [FirstSecondLevelCacheDemo.java](code/FirstSecondLevelCacheDemo.java).

---

## 11. Spring: `@Transactional` и Spring Data JPA

### 11.1. Границы TX

- TX на **service** use-case, не «на каждый repository-метод по умолчанию в голове».
- Proxy / AOP: self-invocation (`this.method()`) обходит proxy → аннотация не сработает.
- Rollback по умолчанию на unchecked; checked — нет, пока не `rollbackFor`.
- `readOnly = true` — hint (flush/driver/routing), не гарантия read-replica сама по себе.
- `REQUIRES_NEW` — отдельная TX и обычно другой connection из pool; PC не «магически общий».

Антипаттерн:

```java
@Transactional
public void place(Command cmd) {
    order.addLine(...);
    paymentClient.charge(...); // внешний I/O внутри TX → долгий hold connection
}
```

Короткая TX на запись → внешний вызов → отдельная TX/confirm (или outbox).

### 11.2. Spring Data JPA

- `JpaRepository` — proxy; derived query / `@Query` / Specifications / Querydsl.
- Projections и DTO — предпочтительны для read API.
- `@EntityGraph` — fetch plan под метод репозитория; подробно [§6.2](#62-entitygraph).
- `save` на новой vs detached полагается на наличие id / `Persistable` / merge-семантику;
  важно понимать, что происходит под капотом, а не считать `save` универсальным UPSERT.

#### `save` и `saveAndFlush`

`saveAndFlush` — метод **Spring Data JPA** (`JpaRepository`), не JPA-спецификации.
В `SimpleJpaRepository`:

```java
@Transactional
public S saveAndFlush(S entity) {
    S result = save(entity);
    flush();          // entityManager.flush()
    return result;
}
```

**1. `save(entity)`**

| Entity «новая» (`isNew` = true) | Иначе |
|----------------------------------|--------|
| `em.persist(entity)` → тот же instance | `em.merge(entity)` → **managed** instance |

`isNew` смотрит на id / `Persistable` / `@Version` — отсюда ловушки с assigned id и
«лишним» `merge`.

**2. `flush()` = `EntityManager.flush()`**

Синхронизация **всего** persistence context с БД прямо сейчас: отложенные
INSERT/UPDATE/DELETE и dirty checking по **всем** managed entity в PC (не только той, что
передали в `save`). См. [§7](#7-flush-dirty-checking-generators).

SQL уходит в **текущую** транзакцию. **Commit это не делает**: другие TX при обычном
isolation строку ещё не увидят. Rollback после flush откатит и эти изменения.

| | `save` | `saveAndFlush` |
|--|--------|----------------|
| В PC | да (`persist`/`merge`) | то же |
| SQL сразу | обычно нет (до auto-flush / commit) | **да**, принудительный flush |
| Batching / отложенный flush | Hibernate может копить и батчить | flush ограничивает эти оптимизации |
| Constraint / optimistic lock | часто только на commit | можно поймать **внутри** метода TX |

Когда уместен `saveAndFlush`:

- id/строка нужны в БД **до** native SQL, stored procedure, другого JDBC в той же TX;
- нужно поймать `OptimisticLockException` / constraint до конца сервиса;
- редкий `FlushMode.MANUAL` — явная синхронизация.

В обычном CRUD с `@Transactional` и `FlushMode.AUTO` чаще хватает `save` (для уже
managed — и без `save`, достаточно смены полей). Слепой `saveAndFlush` везде — лишний
полный dirty check PC и хуже batching. Есть и `saveAllAndFlush`.

Spring Data **JDBC** — отдельная модель (без lazy PC JPA); не путать по названию.

---

## 12. Когда JDBC, когда JPA

| Задача | Обычно |
|--------|--------|
| CRUD агрегатов, инварианты в модели, unit of work | JPA / Hibernate |
| Сложный reporting: CTE, window, vendor SQL | native / `JdbcTemplate` / JOOQ |
| Массовый import/update миллионов строк | JDBC batch / COPY / StatelessSession |
| Динамические админ-фильтры | Criteria / QueryDSL / JOOQ |
| Простая читалка с полным контролем SQL | JDBC или Spring Data JDBC |

Сильный критерий выбора: нужен ли identity map + dirty checking на графе, или нужен
предсказуемый набор SQL и пропускная способность.

---

## 13. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [JdbcPreparedStatementSemanticsDemo.java](code/JdbcPreparedStatementSemanticsDemo.java) | concatenation vs bind; batch mental model |
| [EntityLifecycleDemo.java](code/EntityLifecycleDemo.java) | состояния entity, identity map, flush/clear |
| [AssociationOwningSideDemo.java](code/AssociationOwningSideDemo.java) | owning vs inverse; почему FK не пишется |
| [OrphanRemovalDemo.java](code/OrphanRemovalDemo.java) | orphanRemoval vs CascadeType.REMOVE; DELETE сироты |
| [NPlusOneFetchDemo.java](code/NPlusOneFetchDemo.java) | 1+N vs join fetch vs IN-batch |
| [PaginationJoinFetchDemo.java](code/PaginationJoinFetchDemo.java) | LIMIT по JOIN ≠ страница родителей; паттерн id→fetch |
| [EntityEqualsHashCodePitfallDemo.java](code/EntityEqualsHashCodePitfallDemo.java) | HashSet + generated id |
| [OptimisticLockVersionDemo.java](code/OptimisticLockVersionDemo.java) | `UPDATE … WHERE version`; конфликт TX |
| [EmbeddableMappingDemo.java](code/EmbeddableMappingDemo.java) | `@Embeddable` в колонках родителя; не отдельная таблица |
| [FirstSecondLevelCacheDemo.java](code/FirstSecondLevelCacheDemo.java) | L1 hit vs L2 hit vs DB; разные Session |

Демо моделируют семантику в JVM без реального драйвера/Hibernate Session — для контрактов.
Поведение провайдера и СУБД сверяют по логам SQL и документации версии.

---

## 14. Вопросы для собеседования и ответы

### 1. Чем JPA отличается от Hibernate?

JPA — спецификация (annotations, `EntityManager`, JPQL, lifecycle, persistence unit).
Hibernate — реализация этой спецификации и набор расширений (Session API, `@BatchSize`,
статистика, тонкая настройка fetch/flush/cache, bytecode enhancement). В приложении
можно писать «в терминах JPA», но на проде часто важны именно свойства Hibernate как
движка (flush, batch, план загрузки графа).

### 2. Что такое persistence context и чем он отличается от connection?

Connection — TCP/сессия с СУБД и носитель JDBC-транзакции. Persistence context — набор
managed entity и отложенных изменений **в приложении** (identity map + dirty checking).
Один TX обычно ассоциирован с одним PC, но это разные абстракции: можно сменить flush
без commit, можно `clear` PC, не закрывая connection. L2 cache — уже shared между PC.

### 3. Зачем identity map? Что будет без неё?

Identity map гарантирует: один id → один instance в PC. Без этого два `find` дали бы
две копии; правки разошлись бы, а flush записал бы недетерминированное состояние.
Повторный `find` без SQL — следствие этого контракта, а не главная цель.

### 4. Когда нужен `merge`, а когда достаточно изменить managed entity?

Если entity загружен в текущем PC (`find` / query) — меняете поля, на flush уйдёт UPDATE;
`merge` не нужен. `merge` — для detached/new графа с id извне: провайдер копирует
состояние в managed instance (иногда с SELECT). Работать нужно с **возвращаемым**
значением. Лишний `merge` на уже managed увеличивает шум и риск неожиданных SQL на графе
с cascade.

### 5. Owning vs inverse side: кто пишет FK?

Владеющая сторона (без `mappedBy` на association, обычно `@ManyToOne` или сторона с
`@JoinColumn` / `@JoinTable`). Inverse (`mappedBy`) отражает связь в Java, но SQL FK
не меняет. Заполнили только `author.books` — в БД `author_id` может остаться `NULL`.
Синхронизацию обеих сторон делают в доменных методах.

### 6. За что отвечает `orphanRemoval`? Чем отличается от `CascadeType.REMOVE`?

`orphanRemoval = true`: отвязка ребёнка от родителя (remove из коллекции / null на
ссылке) → при flush `DELETE` строки-сироты. `CascadeType.REMOVE`: удалили родителя →
каскадный `DELETE` детей. Это разные оси. Уместно на агрегате (`OrderLine`); опасно на
shared (`User`, `Product`). Не путать с «просто убрали из `List`» без orphanRemoval —
в БД строка может остаться.

Демо: [OrphanRemovalDemo.java](code/OrphanRemovalDemo.java).

### 7. Как объяснить и устранить N+1?

Симптом: 1 запрос корня + N запросов ассоциации при обходе. Детект: SQL-логи,
datasource-proxy, Hibernate statistics. Фиксить под use-case: `join fetch` / entity graph,
batch-size/`IN`, DTO-проекция, иногда два явных запроса. `EAGER` на коллекциях —
не лечение, а перенос стоимости на каждый `find`. OSIV маскирует проблему, перенося
lazy-load в сериализацию.

### 8. Почему pagination плохо сочетается с `join fetch` коллекции?

JOIN размножает строки родителя по числу элементов коллекции. `LIMIT`/`OFFSET`
(и `Pageable`) режут этот результат, а не «N родителей» → недобор корней, обрезанные
коллекции, неверный total при count по тому же join. `distinct` в JPQL проблему LIMIT
сам по себе не чинит. Обход: страница id без collection fetch → `JOIN FETCH` /
второй запрос по `IN (:ids)`; count отдельно без fetch. To-one fetch с пагинацией
обычно ок; ломает именно to-many. Защита Hibernate:
`fail_on_pagination_over_collection_fetch`.

Демо: [PaginationJoinFetchDemo.java](code/PaginationJoinFetchDemo.java).

### 9. Что делает FlushMode.AUTO перед JPQL?

Если в PC есть незаписанные изменения, которые могут повлиять на результат query,
Hibernate при AUTO синхронизирует SQL (flush), затем выполняет SELECT. Иначе count/filter
видел бы устаревшую БД относительно unit of work. Для массовых вставок flush душат
осознанно (COMMIT/MANUAL + свой контроль), понимая риск рассинхрона query и PC.

### 10. Почему `IDENTITY` мешает batch insert, а `SEQUENCE` — нет?

`IDENTITY` требует выполнить INSERT, чтобы узнать ключ; драйвер/провайдер часто не могут
отправить пачку вставок и сразу получить все id. `SEQUENCE` (или optimized generators)
позволяет заранее выделить диапазон id (`allocationSize`) и батчить INSERT.
Цена: DDL sequence и `allocationSize` должны совпадать; иначе коллизии/дыры.

### 11. Как работает `@Version` на уровне SQL?

UPDATE включает предикат `WHERE id = ? AND version = ?` и инкремент version в SET.
Если строку уже изменила другая TX, updated row count = 0 → optimistic lock failure.
Это не SERIALIZABLE и не защита любых аномалий: только конфликт на конкретной строке
при устаревшей версии. Клиент ретраит с перечитыванием и повторной бизнес-проверкой.

### 12. Чем опасен `equals`/`hashCode` по generated id?

До присвоения id все объекты часто имеют один `hashCode`; после `persist`/flush id
меняется → объект оказывается не в том bucket `HashSet`, `contains` возвращает false
при том, что iteration ещё «видит» элемент. Ломаются Set-ассоциации и любая логика,
завязанная на hash-структуры. Предпочтительнее стабильный business key или жёсткая
дисциплина «не класть в Set до стабильного id».

### 13. Чем L1 отличается от L2? Когда включать L2?

L1 (persistence context) — всегда, per Session: Java instances, identity map, dirty
checking; два `find` одного id в одной TX → один instance без второго SELECT. L2 —
опциональный shared cache **состояния** entity по id между Session; hit → hydrate в
текущий L1. Имеет смысл на read-mostly справочниках с метриками hit-rate; на горячих
write-агрегатах — stale и дорогая инвалидация. Query cache хранит id результатов и без
L2 обычно бесполезен. Включать L2 «на всё приложение» без профиля — ошибка.

Демо: [FirstSecondLevelCacheDemo.java](code/FirstSecondLevelCacheDemo.java).

### 14. Что не так с длинным `@Transactional` вокруг внешнего HTTP-вызова?

На время вызова удерживается connection из pool (и часто PC). Растёт latency TX,
риск pool exhaustion, сложнее retry/идемпотентность, выше шанс deadlock/timeouts.
Паттерн: короткая TX на локальные инварианты → внешний вызов → отдельная TX на
подтверждение; либо transactional outbox / inbox.

### 15. Когда сознательно уходить с JPA на `JdbcTemplate`/native?

Когда важны точный SQL, throughput и предсказуемый план: reporting с CTE/window,
массовые UPDATE/INSERT, ETL, «тонкие» read-модели без графа entity. Unit of work и
dirty checking на миллионах строк раздувают память и генерируют лишний SQL. Нормальная
архитектура — гибрид: JPA на агрегатах OLTP, JDBC/JOOQ на bulk и сложных выборках.

### 16. Чем `getReference` отличается от `find`?

`find` сразу загружает состояние (или возвращает null). `getReference` отдаёт proxy
с известным id без SELECT до обращения к полям/ленивым ассоциациям (если id невалиден,
ошибка всплывёт позже). Удобно выставлять `@ManyToOne` по id без лишнего SELECT;
опасно, если дальше сериализуют proxy или предполагают, что сущность точно существует.

### 17. Что такое `@Embeddable`?

Value object без своей таблицы и `@Id`: поля лежат в колонках entity-владельца
(`@Embedded`). Lifecycle = родитель. Не путать с `@Entity` / `@OneToOne`. Демо:
[EmbeddableMappingDemo.java](code/EmbeddableMappingDemo.java). См. [§3.1](#31-embeddable-и-embedded).

### 18. Как работает `saveAndFlush`? Это commit?

Нет. `save` (`persist` или `merge`) + `EntityManager.flush()`: SQL в текущую TX, commit
отдельно. Flush синхронизирует **весь** PC, не одну entity. Нужен, когда строка должна
быть в БД до native/procedure в той же TX или чтобы поймать optimistic lock / constraint
до конца метода. Иначе обычно достаточно `save` / auto-flush. См.
[§11.2](#112-spring-data-jpa).

### 19. Что такое EntityGraph и чем `fetchgraph` отличается от `loadgraph`?

Fetch plan под операцию: перечисленные атрибуты грузятся как eager, без глобального
`EAGER` на mapping. **fetchgraph** — узлы вне graph считаются lazy (режет граф).
**loadgraph** — вне graph остаётся mapping fetch. В Spring Data — `@EntityGraph` /
`attributePaths` на методе репозитория. Альтернатива — `join fetch` в JPQL. См.
[§6.2](#62-entitygraph).
