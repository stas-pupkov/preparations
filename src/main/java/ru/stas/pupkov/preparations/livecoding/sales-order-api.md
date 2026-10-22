# ТЗ: заказы `sales_order` / `sales_order_line` + REST

## Суть

Spring Boot: таблицы заказа и строк, Entity, cascade delete строк при удалении заказа, простой REST.

## Схема (DDL)

```sql
CREATE TABLE sales_order (
    id              BIGSERIAL PRIMARY KEY,          -- или BIGINT GENERATED ...
    description     VARCHAR(25)  NOT NULL,
    integration_id  INT          NOT NULL UNIQUE
);

CREATE TABLE sales_order_line (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT       NOT NULL
                   REFERENCES sales_order(id) ON DELETE CASCADE,
    item_no    VARCHAR(20),
    quantity   INT          NOT NULL DEFAULT 1,
    amount     NUMERIC(19,2) NOT NULL DEFAULT 0    -- «number» в ТЗ → decimal/money
);

CREATE INDEX idx_sales_order_line_item_no ON sales_order_line(item_no);
CREATE INDEX idx_sales_order_line_order_id ON sales_order_line(order_id);
```

Каскад на уровне БД (`ON DELETE CASCADE`) + JPA `CascadeType.REMOVE` / `orphanRemoval` — лучше **оба**, чтобы удаление через SQL и через EntityManager вело себя одинаково.

## JPA (как мапить на собесе)

```text
SalesOrder 1 — * SalesOrderLine
@OneToMany(mappedBy = "order", cascade = ALL, orphanRemoval = true)
@ManyToOne(optional = false) @JoinColumn(name = "order_id")
```

- `amount` → `BigDecimal`, не `double` / не «голый» `Number`.
- `description` max 25 — `@Size(max = 25)` + валидация на create.
- `integration_id` — `@Column(unique = true, nullable = false)`.
- Fetch: для get-by-id обычно `lines` EAGER или `@EntityGraph` / join fetch; для списка — не тащить все строки без нужды.

Удаление заказа: `repository.deleteById(id)` при правильном cascade/orphanRemoval + FK CASCADE.

## REST (минимальный контракт)

| Метод | Путь | Назначение |
|-------|------|------------|
| POST | `/api/sales-orders` | создать заказ + строки |
| GET | `/api/sales-orders/{id}` | по id |
| GET | `/api/sales-orders?integrationId=` | по `integration_id` |
| DELETE | `/api/sales-orders/{id}` | удалить (+ строки) |
| GET | `/api/sales-orders?itemNo=` | заказы, где есть позиция |

Поиск по item — JPQL/`@Query`:

```java
@Query("""
    select distinct o from SalesOrder o
    join o.lines l
    where l.itemNo = :itemNo
    """)
List<SalesOrder> findByItemNo(String itemNo);
```

Ответы: DTO, не отдавать entity напрямую (lazy/JSON циклы `order↔lines`).

## Подводные камни ТЗ

1. **`varchar(25)` на description** — легко словить 500 на длинном тексте; валидировать 400.
2. **`integration_id` unique** — concurrent create → 409 / `DataIntegrityViolation`.
3. **Каскад только в JPA без FK CASCADE** — `jdbcTemplate.delete` / native SQL оставит сироты (или наоборот).
4. **Список по item** без `distinct` — дубли заказа, если две строки с тем же `item_no`.
5. **`amount` default 0** — уточнить: сумма строки или цена; кто считает total.
6. Транзакция на create (order + lines атомарно); delete в `@Transactional`.

## Демо

- [SalesOrderSchemaCascadeDemo.java](code/SalesOrderSchemaCascadeDemo.java) — DDL + insert + delete order + select by `item_no` на H2.

Полноценный `@RestController` в этот репозиторий не подключался (чтобы не раздувать Boot-scan); на собесе достаточно entity + repository + controller по таблице выше.

## Вопросы для собеседования и ответы

**CascadeType.ALL vs REMOVE + orphanRemoval?**  
ALL тянет PERSIST/MERGE — удобно для aggregate root «заказ со строками». orphanRemoval — удаление строки из коллекции удаляет её из БД.

**Где индекс для «заказы с item»?**  
Как минимум `sales_order_line(item_no)`; при больших объёмах — covering / `order_id` include.

**idempotency?**  
ТЗ не требует; `integration_id` часто играет роль внешнего ключа интеграции — create по нему upsert/conflict.
