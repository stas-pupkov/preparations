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

Конкатенация строк в SQL ломает грамматику запроса (SQL injection):

```text
login = alice' OR '1'='1
→ WHERE login = 'alice' OR '1'='1' AND ...
```

Bind (`?` / named в обёртках) передаёт **значение**, а не фрагмент текста.
Кэш планов на стороне СУБД — бонус, не главная причина выбора `PreparedStatement`.

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

На shared reference (`Order → User`) cascade ALL опасен: можно удалить/permanently
затронуть чужую сущность.

`orphanRemoval = true`: удаление элемента из коллекции → `DELETE` строки (для корректно
настроенной bidirectional/owning семантики). Это не то же самое, что «просто убрать из List».

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

### 6.2. LazyInitializationException

Доступ к lazy вне активного PC/TX → исключение (если нет OSIV и proxy не инициализирован).
Лечение: fetch/graph/DTO **внутри** сервиса, а не «открыть Session пошире».

### 6.3. Open Session In View (OSIV)

Session/EM живёт на весь HTTP request. Jackson может трогать lazy-поля «успешно», но:

- connection дольше занимает pool;
- N+1 прячется в сериализации;
- границы загрузки размазаны по view.

В Spring Boot OSIV historically включён по умолчанию; на проде его обычно выключают
и фиксируют fetch на service boundary.

### 6.4. Пагинация + `join fetch` коллекции

JOIN размножает строки родителя → `setFirstResult` / `setMaxResults` (LIMIT/OFFSET)
применяется к неправильному набору. Обходы: два запроса (страница id → fetch graph by ids),
subselect/window, отдельный count-query. Hibernate может требовать
`hibernate.query.fail_on_pagination_over_collection_fetch=true` как защиту.

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

| Кэш | Область | Что хранит |
|-----|---------|------------|
| First level (PC) | один EntityManager / Session | instances entity |
| Second level | shared across sessions (нужен provider) | данные entity по id |
| Query cache | shared | id'шники результата запроса |

Query cache без L2 для entity часто бесполезен: по id всё равно идут в БД.
L2 имеет смысл на read-mostly справочниках; на часто меняющихся агрегатах —
риск stale + стоимость инвалидации. Ассоциации и collection cache усложняют модель.

Включать L2 «для скорости всего приложения» без профиля — типичная ошибка.

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
- `@EntityGraph` — способ задать fetch под метод.
- `save` на новой vs detached полагается на наличие id / `Persistable` / merge-семантику;
  важно понимать, что происходит под капотом, а не считать `save` универсальным UPSERT.

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
| [NPlusOneFetchDemo.java](code/NPlusOneFetchDemo.java) | 1+N vs join fetch vs IN-batch |
| [EntityEqualsHashCodePitfallDemo.java](code/EntityEqualsHashCodePitfallDemo.java) | HashSet + generated id |
| [OptimisticLockVersionDemo.java](code/OptimisticLockVersionDemo.java) | `UPDATE … WHERE version`; конфликт TX |

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

### 6. Как объяснить и устранить N+1?

Симптом: 1 запрос корня + N запросов ассоциации при обходе. Детект: SQL-логи,
datasource-proxy, Hibernate statistics. Фиксить под use-case: `join fetch` / entity graph,
batch-size/`IN`, DTO-проекция, иногда два явных запроса. `EAGER` на коллекциях —
не лечение, а перенос стоимости на каждый `find`. OSIV маскирует проблему, перенося
lazy-load в сериализацию.

### 7. Почему pagination плохо сочетается с `join fetch` коллекции?

JOIN дублирует строку родителя по числу детей. LIMIT/OFFSET режет уже «размноженный»
результат → неверная страница, недобор родителей, дубли. Обход: сначала выбрать id
страницы без fetch коллекции, затем загрузить граф по id; либо иные стратегии (window,
blaze-persistence и т.п.). Count-query для total отдельно.

### 8. Что делает FlushMode.AUTO перед JPQL?

Если в PC есть незаписанные изменения, которые могут повлиять на результат query,
Hibernate при AUTO синхронизирует SQL (flush), затем выполняет SELECT. Иначе count/filter
видел бы устаревшую БД относительно unit of work. Для массовых вставок flush душат
осознанно (COMMIT/MANUAL + свой контроль), понимая риск рассинхрона query и PC.

### 9. Почему `IDENTITY` мешает batch insert, а `SEQUENCE` — нет?

`IDENTITY` требует выполнить INSERT, чтобы узнать ключ; драйвер/провайдер часто не могут
отправить пачку вставок и сразу получить все id. `SEQUENCE` (или optimized generators)
позволяет заранее выделить диапазон id (`allocationSize`) и батчить INSERT.
Цена: DDL sequence и `allocationSize` должны совпадать; иначе коллизии/дыры.

### 10. Как работает `@Version` на уровне SQL?

UPDATE включает предикат `WHERE id = ? AND version = ?` и инкремент version в SET.
Если строку уже изменила другая TX, updated row count = 0 → optimistic lock failure.
Это не SERIALIZABLE и не защита любых аномалий: только конфликт на конкретной строке
при устаревшей версии. Клиент ретраит с перечитыванием и повторной бизнес-проверкой.

### 11. Чем опасен `equals`/`hashCode` по generated id?

До присвоения id все объекты часто имеют один `hashCode`; после `persist`/flush id
меняется → объект оказывается не в том bucket `HashSet`, `contains` возвращает false
при том, что iteration ещё «видит» элемент. Ломаются Set-ассоциации и любая логика,
завязанная на hash-структуры. Предпочтительнее стабильный business key или жёсткая
дисциплина «не класть в Set до стабильного id».

### 12. Когда имеет смысл L2 cache?

Read-mostly данные с низкой частотой инвалидации (справочники, конфиг), измеримый hit-rate,
понятная стратегия согласованности (и TTL/eviction). На горячих write-агрегатах L2 даёт
stale reads и overhead. Query cache без entity L2 обычно бессмысленен: кэшируются id,
а сами entity всё равно грузятся. Включать «глобально» без метрик — ошибка.

### 13. Что не так с длинным `@Transactional` вокруг внешнего HTTP-вызова?

На время вызова удерживается connection из pool (и часто PC). Растёт latency TX,
риск pool exhaustion, сложнее retry/идемпотентность, выше шанс deadlock/timeouts.
Паттерн: короткая TX на локальные инварианты → внешний вызов → отдельная TX на
подтверждение; либо transactional outbox / inbox.

### 14. Когда сознательно уходить с JPA на `JdbcTemplate`/native?

Когда важны точный SQL, throughput и предсказуемый план: reporting с CTE/window,
массовые UPDATE/INSERT, ETL, «тонкие» read-модели без графа entity. Unit of work и
dirty checking на миллионах строк раздувают память и генерируют лишний SQL. Нормальная
архитектура — гибрид: JPA на агрегатах OLTP, JDBC/JOOQ на bulk и сложных выборках.

### 15. Чем `getReference` отличается от `find`?

`find` сразу загружает состояние (или возвращает null). `getReference` отдаёт proxy
с известным id без SELECT до обращения к полям/ленивым ассоциациям (если id невалиден,
ошибка всплывёт позже). Удобно выставлять `@ManyToOne` по id без лишнего SELECT;
опасно, если дальше сериализуют proxy или предполагают, что сущность точно существует.
