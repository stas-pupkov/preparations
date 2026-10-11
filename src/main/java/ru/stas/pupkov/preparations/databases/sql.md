# SQL (Structured Query Language)

Java 17+ / backend. Заметка для изучения и подготовки к собеседованиям.
Фокус — язык запросов, семантика `NULL`, JOIN, агрегаты, ограничения, window-функции
и диалектные нюансы (PostgreSQL / MySQL / SQL Server), которые реально всплывают на
senior-интервью. Теория модели, нормализации, ACID и изоляции — в
[databases.md](databases.md).

## Содержание

1. [Что такое SQL и группы операторов](#1-что-такое-sql-и-группы-операторов)
2. [NULL и трёхзначная логика](#2-null-и-трёхзначная-логика)
3. [SELECT: синтаксис и логический порядок](#3-select-синтаксис-и-логический-порядок)
4. [JOIN](#4-join)
5. [WHERE, GROUP BY, HAVING, ORDER BY](#5-where-group-by-having-order-by)
6. [Агрегаты, EXISTS, предикаты](#6-агрегаты-exists-предикаты)
7. [UNION, INTERSECT, EXCEPT](#7-union-intersect-except)
8. [Ограничения целостности и индексы](#8-ограничения-целостности-и-индексы)
9. [DML vs TRUNCATE, MERGE](#9-dml-vs-truncate-merge)
10. [View, temp tables, процедуры, триггеры, курсоры](#10-view-temp-tables-процедуры-триггеры-курсоры)
11. [Типы данных: даты и числа](#11-типы-данных-даты-и-числа)
12. [Window-функции и PIVOT](#12-window-функции-и-pivot)
13. [Типовые задачи на написание запросов](#13-типовые-задачи-на-писание-запросов)
14. [Практические задачи (с решениями)](#14-практические-задачи-с-решениями)
15. [Демо-код](#15-демо-код)
16. [Вопросы для собеседования и ответы](#16-вопросы-для-собеседования-и-ответы)

---

## 1. Что такое SQL и группы операторов

SQL (Structured Query Language) — декларативный язык для определения схемы и работы
с данными в реляционных СУБД. Пишете *что* нужно получить; *как* (plan) строит
оптимизатор.

Группы (учебная классификация; границы у вендоров плывут):

| Группа | Операторы | Назначение |
|--------|-----------|------------|
| DDL | `CREATE`, `ALTER`, `DROP`, часто `TRUNCATE` | схема |
| DML | `INSERT`, `UPDATE`, `DELETE` | изменение данных |
| DQL | `SELECT` | выборка (часто относят к DML) |
| DCL | `GRANT`, `REVOKE` | права (`DENY` — в основном SQL Server) |
| TCL | `BEGIN`/`START TRANSACTION`, `COMMIT`, `ROLLBACK`, `SAVEPOINT` | транзакции |

```sql
CREATE TABLE orders (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT ON orders TO app_user;
REVOKE DELETE ON orders FROM app_user;
```

SQL — стандарт (ISO) + диалекты. На собесе полезно говорить «в PostgreSQL / в MySQL»
и не выдавать фичу одного вендора за «весь SQL».

---

## 2. NULL и трёхзначная логика

`NULL` — отсутствие значения (unknown / missing), не `0`, не `''`, не `FALSE`.

Сравнения с `NULL` дают **UNKNOWN**, не FALSE:

```sql
SELECT 1 = NULL;          -- NULL (UNKNOWN)
SELECT NULL = NULL;       -- NULL
SELECT NULL IS NULL;      -- TRUE
SELECT NULL IS NOT NULL;  -- FALSE

-- WHERE оставляет только TRUE; UNKNOWN отфильтровывается как «не подходит»
SELECT * FROM t WHERE col = NULL;      -- всегда пусто; писать IS NULL
SELECT * FROM t WHERE col IS NULL;
SELECT * FROM t WHERE col <> 'x';     -- строки с col NULL не попадут
```

Полезные функции: `COALESCE(a, b, …)`, `NULLIF(a, b)`, `IS DISTINCT FROM`
(PostgreSQL: сравнение, где `NULL` считается «значением» для неравенства).

Демо логики: [SqlNullThreeValuedLogicDemo.java](code/SqlNullThreeValuedLogicDemo.java).

---

## 3. SELECT: синтаксис и логический порядок

Упрощённый каркас:

```sql
SELECT [DISTINCT] expr AS alias, ...
FROM table_or_join
WHERE predicate          -- строки до группировки
GROUP BY expr, ...
HAVING predicate         -- группы после агрегации
ORDER BY expr [ASC|DESC] [NULLS FIRST|LAST]
LIMIT n OFFSET m;        -- LIMIT/OFFSET — PostgreSQL/MySQL; в T-SQL — TOP / OFFSET FETCH
```

**Логический** порядок обработки (не путать с порядком написания):

1. `FROM` / `JOIN`
2. `WHERE`
3. `GROUP BY`
4. `HAVING`
5. `SELECT` (в т.ч. window-функции — после группировки)
6. `DISTINCT`
7. `ORDER BY`
8. `LIMIT` / `OFFSET` / `FETCH`

Отсюда классическая ошибка: в `WHERE` нельзя ссылаться на alias из `SELECT` и на
агрегаты; для фильтра по агрегату — `HAVING` или subquery / CTE.

```sql
-- плохо: alias ещё «не существует» на этапе WHERE (в большинстве СУБД)
SELECT amount * 1.2 AS with_tax
FROM orders
WHERE with_tax > 100;

-- нормально
SELECT amount * 1.2 AS with_tax
FROM orders
WHERE amount * 1.2 > 100;
```

---

## 4. JOIN

JOIN — соединение строк по условию (или декартово произведение).

```sql
SELECT e.name, d.title
FROM employees e
JOIN departments d ON d.id = e.dept_id;
```

| Тип | Результат |
|-----|-----------|
| `INNER JOIN` | только совпавшие пары |
| `LEFT OUTER JOIN` | все слева + совпадения справа, иначе `NULL` |
| `RIGHT OUTER JOIN` | симметрично справа |
| `FULL OUTER JOIN` | все с обеих сторон (в MySQL долго не было нативно) |
| `CROSS JOIN` | декартово произведение |

```sql
-- LEFT: сотрудники без отдела остаются
SELECT e.name, d.title
FROM employees e
LEFT JOIN departments d ON d.id = e.dept_id;

-- «сотрудники без отдела»
SELECT e.*
FROM employees e
LEFT JOIN departments d ON d.id = e.dept_id
WHERE d.id IS NULL;
```

Анти-/semi-join:

```sql
-- semi-join: клиенты, у которых есть заказы (без размножения строк)
SELECT c.*
FROM customers c
WHERE EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.id
);

-- anti-join
SELECT c.*
FROM customers c
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.id
);
```

**JOIN vs subquery.** Нет универсального «всегда JOIN». Современные оптимизаторы часто
переписывают correlated subquery ↔ join. Практика:

- нужны столбцы из нескольких таблиц → JOIN (или lateral);
- «существует / не существует» → `EXISTS` / `NOT EXISTS` (часто лучше `IN` с NULL-ловушками);
- скалярный агрегат для сравнения → subquery / CTE.

Демо: [SqlJoinTypesDemo.java](code/SqlJoinTypesDemo.java),
[SqlExistsSemiJoinDemo.java](code/SqlExistsSemiJoinDemo.java).

---

## 5. WHERE, GROUP BY, HAVING, ORDER BY

| Предложение | Когда работает | Типичное содержимое |
|-------------|----------------|-------------------|
| `WHERE` | до группировки | предикаты по строкам, без агрегатов |
| `GROUP BY` | группировка | ключи групп; негруппированные столбцы в SELECT — ошибка (strict mode) |
| `HAVING` | после агрегатов | фильтр групп, часто с `COUNT`/`SUM` |
| `ORDER BY` | в конце | сортировка результата; порядок без него **не гарантирован** |

```sql
SELECT user_id, COUNT(*) AS cnt
FROM orders
WHERE created_at >= DATE '2024-01-01'   -- отсекли строки
GROUP BY user_id
HAVING COUNT(*) >= 5                    -- отсекли группы
ORDER BY cnt DESC;
```

`GROUP BY` и `NULL`: все `NULL` в ключе группировки попадают в **одну** группу.

`DISTINCT` vs `GROUP BY`:

```sql
SELECT DISTINCT user_id FROM orders;
SELECT user_id FROM orders GROUP BY user_id;  -- тот же смысл без агрегатов

-- GROUP BY нужен, когда считаем агрегаты по группе
SELECT user_id, SUM(amount) FROM orders GROUP BY user_id;
```

`ORDER BY` допускает несколько ключей, `ASC`/`DESC`. В PostgreSQL важен
`NULLS FIRST` / `NULLS LAST` (default для `ASC` — `NULLS LAST`).

---

## 6. Агрегаты, EXISTS, предикаты

Базовые агрегаты: `COUNT`, `SUM`, `AVG`, `MIN`, `MAX` (плюс `bool_and`/`bool_or`,
`array_agg`, `string_agg` / `GROUP_CONCAT` — по диалекту).

```sql
COUNT(*)           -- число строк
COUNT(discount)    -- число не-NULL значений discount
COUNT(DISTINCT user_id)

SUM(amount)        -- NULL в аргументах пропускаются; SUM пустого множества → NULL
COALESCE(SUM(amount), 0)
```

Демо: [SqlCountAggregateDemo.java](code/SqlCountAggregateDemo.java).

Предикаты:

```sql
WHERE status IN ('NEW', 'PAID')
WHERE amount BETWEEN 100 AND 200          -- включительно; NULL → UNKNOWN
WHERE name LIKE 'A%\_x' ESCAPE '\'        -- % и _; ESCAPE по диалекту
WHERE name ILIKE '%smith%'                -- PostgreSQL: case-insensitive
WHERE email ~ '^[a-z]+@'                  -- PostgreSQL regex
```

`EXISTS` — полусоединение: достаточно одной строки в subquery, значения столбцов
подзапроса не важны:

```sql
SELECT *
FROM products p
WHERE EXISTS (
    SELECT 1 FROM order_items i WHERE i.product_id = p.id AND i.qty > 0
);
```

Ловушка `NOT IN` с `NULL`:

```sql
-- если subquery вернёт хотя бы один NULL, NOT IN даст пустой результат
WHERE id NOT IN (SELECT manager_id FROM employees); -- опасно
WHERE NOT EXISTS (SELECT 1 FROM employees e WHERE e.manager_id = t.id);
```

---

## 7. UNION, INTERSECT, EXCEPT

Операции над **совместимыми** результами (число и типы столбцов).

| Оператор | Смысл | Дубликаты |
|----------|--------|-----------|
| `UNION` | объединение | убирает |
| `UNION ALL` | объединение | сохраняет (обычно быстрее) |
| `INTERSECT` | пересечение | убирает (есть `INTERSECT ALL`) |
| `EXCEPT` (`MINUS` в Oracle) | левый минус правый | убирает |

```sql
SELECT email FROM users
UNION ALL
SELECT email FROM leads
ORDER BY 1;   -- ORDER BY относится ко всему объединению
```

Порядок строк без `ORDER BY` не определён. Это стандартный SQL, не только T-SQL.

---

## 8. Ограничения целостности и индексы

| Constraint | Смысл |
|------------|--------|
| `PRIMARY KEY` | уникальность + `NOT NULL`; один на таблицу |
| `UNIQUE` | уникальность; политика по `NULL` — **диалект** |
| `FOREIGN KEY` | ссылочная целостность; сам FK-столбец может быть `NULL`, если нет `NOT NULL` |
| `CHECK` | предикат на строку / таблицу |
| `NOT NULL` | обязательность |

```sql
CREATE TABLE users (
    id    BIGSERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    age   INT CHECK (age IS NULL OR age >= 0)
);

CREATE TABLE orders (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT
);
```

Нюансы, которые режут на собесе:

- **Кластерный индекс у PK** — модель SQL Server / InnoDB (PK ≈ clustered). В PostgreSQL
  PK → unique B-tree индекс, «кластер» как у SQL Server нет (есть `CLUSTER` как операция).
- **`UNIQUE` и NULL**: в PostgreSQL несколько строк с `NULL` в unique-столбце допустимы
  (`NULL`≠`NULL`). В SQL Server исторически — иначе; смотреть версию / filtered unique index.
- Индекс создаётся явно или как побочный эффект PK/UNIQUE:

```sql
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email)); -- expression index (PG)
```

Подробнее про selectivity и trade-offs индексов — [databases.md](databases.md)
и [IndexSelectivityDemo.java](code/IndexSelectivityDemo.java).

---

## 9. DML vs TRUNCATE, MERGE

```sql
DELETE FROM orders WHERE created_at < DATE '2020-01-01'; -- построчно, WHERE, триггеры
TRUNCATE TABLE orders; -- быстрая очистка таблицы целиком
```

| | `DELETE` | `TRUNCATE` |
|--|----------|------------|
| Класс | DML | у большинства СУБД DDL / DDL-like |
| `WHERE` | да | нет |
| Триггеры | row/statement triggers | обычно не те же / ограничено |
| Транзакция | обычный DML | в PG можно rollback; в MySQL — неявно коммитит в многих случаях |
| IDENTITY / sequence | не сбрасывает само | часто сброс (опции зависят от СУБД) |
| FK | можно точечно | нельзя, если на таблицу ссылаются (типично) |

Фраза «TRUNCATE дропает и создаёт таблицу заново» — грубая метафора реализации, не
контракт. На собесе лучше: минимальное логирование, целиком освобождает страницы,
без построчного undo как у `DELETE`.

`MERGE` (SQL:2003; UPSERT-паттерн) — insert или update по правилу сопоставления:

```sql
-- идея (синтаксис сильно отличается по СУБД)
MERGE INTO target t
USING source s ON t.id = s.id
WHEN MATCHED THEN UPDATE SET t.v = s.v
WHEN NOT MATCHED THEN INSERT (id, v) VALUES (s.id, s.v);

-- PostgreSQL чаще:
INSERT INTO target (id, v) VALUES (1, 'a')
ON CONFLICT (id) DO UPDATE SET v = EXCLUDED.v;
```

---

## 10. View, temp tables, процедуры, триггеры, курсоры

**View** — именованный сохранённый `SELECT`. Данных своих обычно нет (кроме
materialized view).

```sql
CREATE VIEW active_users AS
SELECT id, email FROM users WHERE status = 'ACTIVE';

-- materialized (PostgreSQL): физический снимок, нужен REFRESH
CREATE MATERIALIZED VIEW sales_daily AS
SELECT day, SUM(amount) FROM sales GROUP BY day;
```

Зачем: стабильный API схемы, security barrier (дать доступ к срезу), упрощение запросов.
Updatable view — с ограничениями; сложную логику обновления иногда закрывают `INSTEAD OF`
триггерами.

**Временные таблицы** — живут сессию / транзакцию (зависит от СУБД и вида):

```sql
-- PostgreSQL
CREATE TEMP TABLE tmp_ids (id BIGINT PRIMARY KEY) ON COMMIT DROP;
```

Используют для промежуточных результатов, разгрузки сложных планов, пакетной обработки.
Часто CTE (`WITH`) достаточно и читаемее; temp table выигрывает при многократном
переиспользовании большого промежуточного набора.

**Хранимая процедура / функция** — код на стороне БД (PL/pgSQL, T-SQL, …). Плюсы: меньше
round-trips, права `EXECUTE`, единая точка для batch-логики. Минусы: хуже переносимость,
сложнее CI/тесты/версионирование, чем app-код. В PostgreSQL важно различать `FUNCTION`
и `PROCEDURE` (и `RETURNS` / outbound params).

**Триггер** — реакция на `INSERT`/`UPDATE`/`DELETE` (`BEFORE`/`AFTER`, row/statement).
Хорош для аудита и жёстких инвариантов рядом с данными; плох как свалка бизнес-логики
(скрытые side effects, сложная отладка).

**Курсор** — построчный обход результата в процедурном коде. В set-based SQL почти всегда
хуже по производительности. В приложении «курсор» JDBC (`ResultSet`) — другая история:
streaming строк клиенту, не PL-курсор СУБД.

---

## 11. Типы данных: даты и числа

Не опирайтесь на «DATETIME 8 байт / TIMESTAMP 4 байта» как на универсальную истину —
это старое описание **MySQL**. Актуальная картина ближе к такой:

| СУБД | Тип | Смысл |
|------|-----|--------|
| PostgreSQL | `TIMESTAMP` | дата-время **без** таймзоны |
| PostgreSQL | `TIMESTAMPTZ` | момент времени; хранение UTC, показ в session TZ |
| MySQL | `DATETIME` | «настенные часы» без TZ |
| MySQL | `TIMESTAMP` | с конвертацией в/из time_zone (и свой диапазон) |
| SQL Server | `DATETIME` / `DATETIME2` / `DATETIMEOFFSET` | разные точность и TZ |

Практика для backend: хранить момент как `TIMESTAMPTZ` (PG) / `DATETIMEOFFSET` /
epoch+UTC; «локальную дату события» — отдельно как `DATE`/`TIMESTAMP without time zone`,
если это именно календарная дата.

Числа:

- `INTEGER` / `BIGINT` — точная целочисленная арифметика;
- `NUMERIC`/`DECIMAL` — точные десятичные (деньги);
- `REAL`/`DOUBLE PRECISION` — IEEE float, ошибки округления;
- `BIT` в SQL Server — не полноценный числовой тип для `+`/`-` в привычном смысле;
  в PostgreSQL `bit` — bit string, для флагов чаще `BOOLEAN`.

```sql
-- деньги
amount NUMERIC(12, 2) NOT NULL
```

---

## 12. Window-функции и PIVOT

Window-функции (стандарт SQL, не только T-SQL) считают выражение по **окну** строк,
не схлопывая результат как `GROUP BY`.

```sql
SELECT
    student,
    marks,
    ROW_NUMBER() OVER (ORDER BY marks DESC) AS rn,
    RANK()       OVER (ORDER BY marks DESC) AS rnk,
    DENSE_RANK() OVER (ORDER BY marks DESC) AS dense,
    NTILE(4)     OVER (ORDER BY marks DESC) AS quartile,
    AVG(marks)   OVER (PARTITION BY class)  AS class_avg
FROM exam_result;
```

| Функция | При ничьей |
|---------|------------|
| `ROW_NUMBER` | разные номера всегда |
| `RANK` | одинаковый ранг, следующий с пропуском |
| `DENSE_RANK` | одинаковый ранг, **без** пропуска |
| `NTILE(n)` | делит упорядоченный набор на `n` корзин |

Демо: [SqlWindowRankingDemo.java](code/SqlWindowRankingDemo.java).

Типичные задачи: top-N per group, бегущая сумма, детект дубликатов, пагинация стабильнее
«OFFSET на больших страницах» через keyset + `ROW_NUMBER`/seek.

`PIVOT` / `UNPIVOT` — удобный синтаксис SQL Server. В PostgreSQL / стандартном SQL обычно
`FILTER`, `CASE` + агрегат или `crosstab` (tablefunc):

```sql
SELECT
    user_id,
    COUNT(*) FILTER (WHERE status = 'NEW')  AS new_cnt,
    COUNT(*) FILTER (WHERE status = 'PAID') AS paid_cnt
FROM orders
GROUP BY user_id;
```

---

## 13. Типовые задачи на написание запросов

### 13.1. Строка с максимальным id

```sql
-- корректно и ясно
SELECT id, created
FROM t
WHERE id = (SELECT MAX(id) FROM t);

-- или
SELECT id, created
FROM t
ORDER BY id DESC
LIMIT 1;

-- оконкой (удобно обобщать на «топ-N»)
SELECT id, created
FROM (
    SELECT id, created, ROW_NUMBER() OVER (ORDER BY id DESC) AS rn
    FROM t
) x
WHERE rn = 1;
```

### 13.2. Распределение: сколько пользователей сделали ровно N скачиваний за день

```sql
SELECT download_count, COUNT(*) AS user_count
FROM (
    SELECT user_id, COUNT(*) AS download_count
    FROM track_downloads
    WHERE download_time >= TIMESTAMP '2010-11-19'
      AND download_time <  TIMESTAMP '2010-11-20'
    GROUP BY user_id
) per_user
GROUP BY download_count
ORDER BY download_count;
```

Замечания senior-уровня:

- сравнивать timestamp через полуинтервал `[day, next_day)`, а не `=` со строкой;
- `DISTINCT` на внешней группировке не нужен, если уже `GROUP BY download_count`;
- индекс под фильтр/группировку: `(download_time, user_id)` или подобный.

---

## 14. Практические задачи (с решениями)

Схема (PostgreSQL-ориентированный синтаксис). Все ожидаемые таблицы ниже считаются
по **одному и тому же** набору данных. Для задач с «последними 7 днями» фиксируем
«сейчас» = `2024-06-15 12:00:00+00`.

```sql
CREATE TABLE departments (
    id   BIGINT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE employees (
    id            BIGINT PRIMARY KEY,
    name          TEXT NOT NULL,
    department_id BIGINT REFERENCES departments(id),
    manager_id    BIGINT REFERENCES employees(id), -- NULL у топ-менеджера
    salary        NUMERIC(12, 2) NOT NULL,
    hired_at      DATE NOT NULL
);

CREATE TABLE customers (
    id    BIGINT PRIMARY KEY,
    name  TEXT NOT NULL,
    email TEXT NOT NULL
);

CREATE TABLE orders (
    id          BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    amount      NUMERIC(12, 2) NOT NULL,
    status      TEXT NOT NULL,          -- NEW / PAID / CANCELLED
    created_at  TIMESTAMPTZ NOT NULL
);
```

### Исходные данные

`departments`

| id | name |
|----|------|
| 1 | Engineering |
| 2 | HR |

`employees`

| id | name | department_id | manager_id | salary | hired_at |
|----|------|---------------|------------|--------|----------|
| 1 | Alice | 1 | NULL | 200000.00 | 2019-01-01 |
| 2 | Bob | 1 | 1 | 150000.00 | 2020-06-01 |
| 3 | Carol | 1 | 1 | 150000.00 | 2021-03-01 |
| 4 | Dave | 1 | 1 | 120000.00 | 2022-01-01 |
| 5 | Eve | 2 | NULL | 100000.00 | 2018-01-01 |
| 6 | Frank | 2 | 5 | 90000.00 | 2021-01-01 |

`customers`

| id | name | email |
|----|------|-------|
| 1 | Ann | ann@example.com |
| 2 | Bob | bob@example.com |
| 3 | Clone | ann@example.com |
| 4 | Dana | dana@example.com |

`orders`

| id | customer_id | amount | status | created_at (UTC) |
|----|-------------|--------|--------|------------------|
| 1 | 1 | 4000.00 | PAID | 2024-01-10 |
| 2 | 1 | 4000.00 | PAID | 2024-02-15 |
| 3 | 1 | 3000.00 | PAID | 2024-03-20 |
| 4 | 1 | 500.00 | NEW | 2024-06-14 |
| 5 | 2 | 2000.00 | PAID | 2024-01-05 |
| 6 | 2 | 1000.00 | CANCELLED | 2024-02-01 |
| 7 | 3 | 500.00 | PAID | 2024-06-10 |
| 8 | 4 | 8000.00 | PAID | 2023-12-01 |
| 9 | 2 | 1500.00 | PAID | 2024-06-12 |
| 10 | 1 | 700.00 | PAID | 2024-06-13 |

### 14.1. Базовый SELECT с условиями — средняя зарплата

**Задача.** Средняя зарплата сотрудников отдела `Engineering`, нанятых не раньше
`2020-01-01`. Округлить до 2 знаков.

Alice (2019) не входит. В выборке: Bob, Carol, Dave → `(150000+150000+120000)/3`.

**Ожидаемый результат**

| avg_salary |
|------------|
| 140000.00 |

```sql
SELECT ROUND(AVG(e.salary), 2) AS avg_salary
FROM employees e
JOIN departments d ON d.id = e.department_id
WHERE d.name = 'Engineering'
  AND e.hired_at >= DATE '2020-01-01';
```

Нюанс: `AVG` игнорирует `NULL` в столбце; при пустой выборке `AVG` вернёт `NULL`, не `0`
(`COALESCE` по желанию).

### 14.2. GROUP BY и агрегаты — сумма заказов по клиентам

**Задача.** Для каждого клиента: число заказов и сумма `amount` только по статусу `PAID`.
Отсортировать по сумме убыванию.

**Ожидаемый результат**

| id | name | paid_orders | paid_total |
|----|------|-------------|------------|
| 1 | Ann | 4 | 11700.00 |
| 4 | Dana | 1 | 8000.00 |
| 2 | Bob | 2 | 3500.00 |
| 3 | Clone | 1 | 500.00 |

```sql
SELECT
    c.id,
    c.name,
    COUNT(*)            AS paid_orders,
    SUM(o.amount)       AS paid_total
FROM customers c
JOIN orders o ON o.customer_id = c.id
WHERE o.status = 'PAID'
GROUP BY c.id, c.name
ORDER BY paid_total DESC;
```

Если нужны и клиенты **без** оплаченных заказов — `LEFT JOIN` + `WHERE` заменить на
`COUNT(o.id)` / `COALESCE(SUM(o.amount), 0)` и условие статуса в `ON` или `FILTER`.

### 14.3. Поиск дубликатов — HAVING с COUNT

**Задача.** Найти email, которые встречаются у более чем одного клиента.

**Ожидаемый результат**

| email | cnt |
|-------|-----|
| ann@example.com | 2 |

```sql
SELECT email, COUNT(*) AS cnt
FROM customers
GROUP BY email
HAVING COUNT(*) > 1
ORDER BY cnt DESC, email;
```

Вариант «показать сами строки-дубликаты» — ожидаемо Ann и Clone:

| id | name | email |
|----|------|-------|
| 1 | Ann | ann@example.com |
| 3 | Clone | ann@example.com |

```sql
SELECT c.*
FROM customers c
WHERE c.email IN (
    SELECT email
    FROM customers
    GROUP BY email
    HAVING COUNT(*) > 1
)
ORDER BY c.id;
```

### 14.4. Работа с датами — агрегация по месяцам

**Задача.** Сумма оплаченных заказов по календарным месяцам (UTC).

**Ожидаемый результат**

| month_utc | revenue |
|-----------|---------|
| 2023-12-01 00:00:00+00 | 8000.00 |
| 2024-01-01 00:00:00+00 | 6000.00 |
| 2024-02-01 00:00:00+00 | 4000.00 |
| 2024-03-01 00:00:00+00 | 3000.00 |
| 2024-06-01 00:00:00+00 | 2700.00 |

```sql
SELECT
    DATE_TRUNC('month', o.created_at AT TIME ZONE 'UTC') AS month_utc,
    SUM(o.amount) AS revenue
FROM orders o
WHERE o.status = 'PAID'
GROUP BY 1
ORDER BY 1;
```

В MySQL чаще `DATE_FORMAT(created_at, '%Y-%m-01')` или `DATE_FORMAT(..., '%Y-%m')`.
Не группировать по `to_char(...)` без нужды, если дальше джойните к календарю —
удобнее держать `date`/`timestamptz` границы месяца.

### 14.5. Оконные функции — ранжирование в группах

**Задача.** В каждом отделе пронумеровать сотрудников по убыванию зарплаты
(`ROW_NUMBER`) и отдельно поставить плотный рейтинг (`DENSE_RANK`).

**Ожидаемый результат**

| department_id | employee_id | name | salary | salary_rn | salary_dense_rank |
|---------------|-------------|------|--------|-----------|-------------------|
| 1 | 1 | Alice | 200000.00 | 1 | 1 |
| 1 | 2 | Bob | 150000.00 | 2 | 2 |
| 1 | 3 | Carol | 150000.00 | 3 | 2 |
| 1 | 4 | Dave | 120000.00 | 4 | 3 |
| 2 | 5 | Eve | 100000.00 | 1 | 1 |
| 2 | 6 | Frank | 90000.00 | 2 | 2 |

У Bob и Carol одинаковая зарплата: `ROW_NUMBER` разводит 2 и 3, `DENSE_RANK` оба = 2.

```sql
SELECT
    e.department_id,
    e.id AS employee_id,
    e.name,
    e.salary,
    ROW_NUMBER() OVER (
        PARTITION BY e.department_id
        ORDER BY e.salary DESC, e.id
    ) AS salary_rn,
    DENSE_RANK() OVER (
        PARTITION BY e.department_id
        ORDER BY e.salary DESC
    ) AS salary_dense_rank
FROM employees e
ORDER BY e.department_id, salary_rn;
```

`e.id` в `ORDER BY` у `ROW_NUMBER` делает нумерацию детерминированной при равных зарплатах.

### 14.6. Работа с NULL — сотрудники без менеджеров

**Задача.** Все сотрудники, у которых нет менеджера (`manager_id IS NULL`). Вторым
запросом — anti-join: менеджер указан, но строки нет (на наших данных с FK результат
пустой).

**Ожидаемый результат** (топ-уровень):

| id | name | department_id | salary |
|----|------|---------------|--------|
| 1 | Alice | 1 | 200000.00 |
| 5 | Eve | 2 | 100000.00 |

**Ожидаемый результат** (битые ссылки на менеджера) — пусто:

| id | name | manager_id |
|----|------|------------|
| *(нет строк)* | | |

```sql
-- топ-уровень иерархии
SELECT id, name, department_id, salary
FROM employees
WHERE manager_id IS NULL
ORDER BY id;

-- LEFT JOIN anti-join: «менеджер не найден»
SELECT e.id, e.name, e.manager_id
FROM employees e
LEFT JOIN employees m ON m.id = e.manager_id
WHERE e.manager_id IS NOT NULL
  AND m.id IS NULL;
```

Не писать `manager_id = NULL`. Демо семантики:
[SqlNullThreeValuedLogicDemo.java](code/SqlNullThreeValuedLogicDemo.java).

### 14.7. JOIN таблиц — заказы и клиенты

**Задача.** Вывести заказы с именем и email клиента (`LEFT JOIN`), новее сверху.

**Ожидаемый результат**

| order_id | amount | status | created_at | customer_name | customer_email |
|----------|--------|--------|------------|---------------|----------------|
| 4 | 500.00 | NEW | 2024-06-14 | Ann | ann@example.com |
| 10 | 700.00 | PAID | 2024-06-13 | Ann | ann@example.com |
| 9 | 1500.00 | PAID | 2024-06-12 | Bob | bob@example.com |
| 7 | 500.00 | PAID | 2024-06-10 | Clone | ann@example.com |
| 3 | 3000.00 | PAID | 2024-03-20 | Ann | ann@example.com |
| 2 | 4000.00 | PAID | 2024-02-15 | Ann | ann@example.com |
| 6 | 1000.00 | CANCELLED | 2024-02-01 | Bob | bob@example.com |
| 1 | 4000.00 | PAID | 2024-01-10 | Ann | ann@example.com |
| 5 | 2000.00 | PAID | 2024-01-05 | Bob | bob@example.com |
| 8 | 8000.00 | PAID | 2023-12-01 | Dana | dana@example.com |

```sql
SELECT
    o.id AS order_id,
    o.amount,
    o.status,
    o.created_at,
    c.name  AS customer_name,
    c.email AS customer_email
FROM orders o
LEFT JOIN customers c ON c.id = o.customer_id
ORDER BY o.created_at DESC;
```

`INNER JOIN` отбросит «осиротевшие» заказы — выбирайте осознанно.

### 14.8. Комбинированные условия — JOIN + GROUP BY + HAVING

**Задача.** Клиенты, у которых за 2024 год сумма оплаченных заказов ≥ 10_000 и при этом
не меньше 3 оплаченных заказов. Вернуть имя, число заказов, сумму.

Ann в 2024: 4×PAID на 11700. Bob: 2 заказа / 3500 — не проходит. Dana: оплата в 2023.

**Ожидаемый результат**

| id | name | paid_orders | paid_total |
|----|------|-------------|------------|
| 1 | Ann | 4 | 11700.00 |

```sql
SELECT
    c.id,
    c.name,
    COUNT(*)      AS paid_orders,
    SUM(o.amount) AS paid_total
FROM customers c
JOIN orders o ON o.customer_id = c.id
WHERE o.status = 'PAID'
  AND o.created_at >= TIMESTAMP '2024-01-01 00:00:00+00'
  AND o.created_at <  TIMESTAMP '2025-01-01 00:00:00+00'
GROUP BY c.id, c.name
HAVING COUNT(*) >= 3
   AND SUM(o.amount) >= 10000
ORDER BY paid_total DESC;
```

Фильтр по сырым строкам — в `WHERE`; по агрегатам — в `HAVING`.

### 14.9. Даты и интервалы — анализ за последние 7 дней

**Задача.** По каждому дню за последние 7 суток (включая «сегодня» `2024-06-15`):
число заказов и выручка по `PAID`. Ниже — вариант с полным календарём и нулями.

Окно: `2024-06-09` … `2024-06-15`. PAID в окне: 10-е (500), 12-е (1500), 13-е (700).

**Ожидаемый результат** (календарь + нули)

| day_utc | paid_orders | revenue |
|---------|-------------|---------|
| 2024-06-09 | 0 | 0.00 |
| 2024-06-10 | 1 | 500.00 |
| 2024-06-11 | 0 | 0.00 |
| 2024-06-12 | 1 | 1500.00 |
| 2024-06-13 | 1 | 700.00 |
| 2024-06-14 | 0 | 0.00 |
| 2024-06-15 | 0 | 0.00 |

```sql
-- для воспроизведения фиксируем «сейчас»
-- SET TIME ZONE 'UTC';
-- и подставьте timestamptz '2024-06-15 12:00:00+00' вместо now()

WITH days AS (
    SELECT generate_series(
               DATE '2024-06-09',
               DATE '2024-06-15',
               INTERVAL '1 day'
           )::date AS day_utc
)
SELECT
    d.day_utc,
    COUNT(o.id)                AS paid_orders,
    COALESCE(SUM(o.amount), 0) AS revenue
FROM days d
LEFT JOIN orders o
       ON o.status = 'PAID'
      AND (o.created_at AT TIME ZONE 'UTC')::date = d.day_utc
GROUP BY d.day_utc
ORDER BY d.day_utc;
```

Упрощённый вариант без пустых дней вернёт только три строки (10 / 12 / 13 июня).

Для индексов предпочтителен полуинтервал по `timestamptz`, а не `WHERE created_at::date`
(каст часто убивает index scan).

### 14.10. Максимумы в группах — подзапрос vs оконные функции

**Задача.** В каждом отделе найти сотрудника(ов) с максимальной зарплатой.

**Ожидаемый результат** (все три варианта ниже)

| id | name | department_id | manager_id | salary | hired_at |
|----|------|---------------|------------|--------|----------|
| 1 | Alice | 1 | NULL | 200000.00 | 2019-01-01 |
| 5 | Eve | 2 | NULL | 100000.00 | 2018-01-01 |

**Вариант A — коррелированный / агрегатный подзапрос:**

```sql
SELECT e.*
FROM employees e
WHERE e.salary = (
    SELECT MAX(e2.salary)
    FROM employees e2
    WHERE e2.department_id = e.department_id
)
ORDER BY e.department_id;
```

**Вариант B — JOIN к агрегату:**

```sql
SELECT e.*
FROM employees e
JOIN (
    SELECT department_id, MAX(salary) AS max_salary
    FROM employees
    GROUP BY department_id
) m ON m.department_id = e.department_id
   AND m.max_salary = e.salary
ORDER BY e.department_id;
```

**Вариант C — окно (удобно расширять до top-N):**

```sql
SELECT id, name, department_id, salary
FROM (
    SELECT
        e.*,
        RANK() OVER (
            PARTITION BY department_id
            ORDER BY salary DESC
        ) AS rnk
    FROM employees e
) x
WHERE rnk = 1
ORDER BY department_id;
```

| Подход | Плюсы | Минусы |
|--------|-------|--------|
| subquery / JOIN+MAX | просто, широко поддерживается | top-N per group неудобнее |
| `RANK`/`DENSE_RANK` | естественные ничьи | чуть тяжелее читать новичкам |
| `ROW_NUMBER` | ровно одна строка на группу | при ничьей «побеждает» произвольный tie-break |

Для «ровно один победитель» — `ROW_NUMBER` + стабильный `ORDER BY`.
Для «все чемпионы при ничьей» — `RANK`/`DENSE_RANK` или сравнение с `MAX`.

---

## 15. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [SqlNullThreeValuedLogicDemo.java](code/SqlNullThreeValuedLogicDemo.java) | UNKNOWN vs FALSE, `IS NULL`, COALESCE |
| [SqlJoinTypesDemo.java](code/SqlJoinTypesDemo.java) | INNER / LEFT / RIGHT / FULL / CROSS |
| [SqlExistsSemiJoinDemo.java](code/SqlExistsSemiJoinDemo.java) | EXISTS не размножает строки как JOIN |
| [SqlCountAggregateDemo.java](code/SqlCountAggregateDemo.java) | `COUNT(*)` vs `COUNT(col)` |
| [SqlWindowRankingDemo.java](code/SqlWindowRankingDemo.java) | `ROW_NUMBER` / `RANK` / `DENSE_RANK` |

Демо моделируют семантику SQL в памяти (без JDBC), чтобы быстро прогнать идею локально.
Живой SQL проверяйте в своей СУБД / docker.

---

## 16. Вопросы для собеседования и ответы

### 1. Почему `WHERE col = NULL` не находит строки с NULL?

Потому что `=` с `NULL` даёт UNKNOWN, а `WHERE` пропускает только TRUE. Нужен
`col IS NULL`. Аналогично `col <> 1` не вернёт строки, где `col` NULL. Демо:
[SqlNullThreeValuedLogicDemo.java](code/SqlNullThreeValuedLogicDemo.java).

### 2. Чем логический порядок SELECT отличается от текстового?

Пишем `SELECT … FROM … WHERE …`, а движок логически делает `FROM/JOIN → WHERE →
GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT`. Поэтому алиасы `SELECT`
нельзя использовать в `WHERE`, а агрегаты — только в `HAVING` / внешнем запросе.

### 3. Когда `EXISTS` предпочтительнее `IN` и `JOIN`?

Для проверки существования — `EXISTS` (semi-join): не раздувает кардинальность и
безопаснее `NOT IN` при NULL в subquery. `JOIN` нужен, когда из правой стороны реально
нужны столбцы. Оптимизатор часто делает похожие планы, но семантика размножения строк
у JOIN другая — см. [SqlExistsSemiJoinDemo.java](code/SqlExistsSemiJoinDemo.java).

### 4. `COUNT(*)` и `COUNT(col)` — в чём подвох для отчётов?

`COUNT(*)` — строки. `COUNT(col)` — не-NULL значения `col`. В отчёте «число заказов со
скидкой» нужен второй; «число заказов» — первый. `SUM`/`AVG` тоже игнорируют NULL.
Демо: [SqlCountAggregateDemo.java](code/SqlCountAggregateDemo.java).

### 5. PRIMARY KEY vs UNIQUE — что сказать кроме «NULL»?

Оба обеспечивают уникальность. PK подразумевает `NOT NULL` и один на таблицу; UNIQUE
может быть несколько. Поведение нескольких NULL в UNIQUE — диалектное. «PK всегда
clustered» — не про PostgreSQL. Физическая кластеризация — свойство конкретной СУБД.

### 6. Чем `DELETE` отличается от `TRUNCATE` на проде?

`DELETE` — DML с `WHERE`, триггерами, обычным MVCC/undo. `TRUNCATE` — быстрая очистка
всей таблицы, обычно DDL-like, без построчного предиката, с ограничениями по FK и
разным поведением автокоммита/identity между СУБД. Для «удалить старые 1% строк» —
`DELETE` (или partition drop). Для «очистить staging целиком» — `TRUNCATE`.

### 7. Как работает `RANK` vs `DENSE_RANK` vs `ROW_NUMBER`?

При ничьих: `ROW_NUMBER` всё равно уникален; `RANK` ставит одинаковый номер и пропускает
следующие; `DENSE_RANK` — одинаковый без пропуска. Нужно для рейтингов, top-N per group
(`ROW_NUMBER … PARTITION BY …` + фильтр `rn <= N`). Демо:
[SqlWindowRankingDemo.java](code/SqlWindowRankingDemo.java).

### 8. Почему `ORDER BY` в subquery «пропадает»?

Без `LIMIT`/`TOP`/`FETCH` порядок внутреннего запроса не обязан сохраниться: отношение
не упорядочено. Наружу порядок задаёт внешний `ORDER BY`. Исключения — когда порядок
реально фиксируется конструкцией, влияющей на кардинальность/выборку (`ORDER BY … LIMIT`).

### 9. В чём опасность `SELECT *` и неявных JOIN в легаси?

`SELECT *` ломает клиентов при добавлении столбцов, тащит лишние TOAST/LOB, мешает
covering index. `FROM a, b WHERE a.id = b.a_id` — старый синтаксис; легко получить
нечаянный CROSS JOIN, забыв условие. Явный `JOIN … ON` читаемее и безопаснее.

### 10. Как объяснить разницу DATETIME и TIMESTAMP, не врАя про байты?

Говорить про **семантику TZ**, а не про устаревшие размеры MySQL. «Настенные часы без
зоны» vs «момент времени с конвертацией». В PostgreSQL пара — `TIMESTAMP` /
`TIMESTAMPTZ`. Выбор зависит от того, календарная это дата события или абсолютный момент.

### 11. Что такое correlated subquery и когда она боль?

Подзапрос, ссылающийся на строки внешнего запроса; логически может выполняться «на
каждую строку». Иногда превращается в nested loop и взрывается на больших таблицах.
Лечится переписыванием в JOIN / `EXISTS` с правильными индексами, декорреляцией
оптимизатором, CTE с предварительной агрегацией.

### 12. Зачем `HAVING`, если можно отфильтровать в приложении?

`HAVING` отсекает группы **на сервере** до передачи клиенту — меньше сети и памяти
приложения. Семантически это фильтр по результату агрегации; перенос в app имеет смысл
только если групп мало и логика фильтрации недекларативна.

### 13. Как бы вы написали «топ-3 товара в каждой категории»?

```sql
SELECT *
FROM (
    SELECT
        category_id,
        product_id,
        sales,
        ROW_NUMBER() OVER (
            PARTITION BY category_id
            ORDER BY sales DESC
        ) AS rn
    FROM product_sales
) t
WHERE rn <= 3;
```

На собесе ожидают window + `PARTITION BY`, обсуждение ничьих (`RANK` vs `ROW_NUMBER`)
и индекс под `(category_id, sales DESC)`.
