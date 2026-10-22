# SQL: `IN` / `NOT IN` / `!=` и `NULL`

## Условие

Запросы к `think_hub_ai.test` (на скрине данных таблицы нет). Типичный учебный набор:

| id | name   |
|----|--------|
| 1  | Dima   |
| 2  | `''`   |
| 3  | Ivan   |
| 4  | NULL   |

(Если `id=3` другое non-null имя ≠ Dima/пусто — логика та же.)

```sql
-- 1
select * from think_hub_ai.test t where name in ('Dima', '', null);

-- 2
select id, name from think_hub_ai.test t
 where name = 'Dima' or name = '' or name is null;

-- 3
select * from think_hub_ai.test t where name not in ('Dima', '', null);

-- 4
select * from think_hub_ai.test t
 where name != 'Dima' and name != '' and name != null;
```

## Что вернёт каждый

| # | Запрос | Результат (id) | Почему |
|---|--------|----------------|--------|
| 1 | `IN ('Dima', '', null)` | **1, 2** | `name = null` даёт UNKNOWN; строка с NULL **не** проходит `IN` |
| 2 | `= … OR = '' OR IS NULL` | **1, 2, 4** | `IS NULL` — единственный корректный способ поймать NULL |
| 3 | `NOT IN (…, null)` | **пусто** | из‑за `null` в списке выражение для любых строк → UNKNOWN |
| 4 | `!= … AND != null` | **пусто** | `name != null` всегда UNKNOWN, не `IS NOT NULL` |

Заметки на скрине (`1, 2, 4` и `3.4`) к корректным результатам **не** совпадают: `1,2,4` — это ответ для **запроса 2**, не для `IN`; оба нижних запроса дают пусто, не «3 и 4».

## Трёхзначная логика (кратко)

- Сравнение с `NULL` (`=`, `!=`, `IN`-элемент) → **UNKNOWN**.
- `WHERE` оставляет только **TRUE** (UNKNOWN как FALSE).
- `x IN (a, b, NULL)` ≡ `x=a OR x=b OR x=NULL` → NULL-строки не матчятся.
- `x NOT IN (a, b, NULL)` ≡ `x<>a AND x<>b AND x<>NULL` → из‑за `<>NULL` весь предикат UNKNOWN → **0 строк**.
- Нужны `IS NULL` / `IS NOT NULL`, не `= null` / `!= null`.

Эквивалент «не Dima, не пусто, не null»:

```sql
where name is distinct from 'Dima'  -- PostgreSQL
  and name is distinct from ''
-- или: name not in ('Dima', '') and name is not null
```

## Демо

- [SqlNullInNotInDemo.java](code/SqlNullInNotInDemo.java)

## Вопросы для собеседования и ответы

**Почему `NOT IN` с `NULL` в списке «убивает» выборку?**  
Потому что для каждого кандидата одно из сравнений `<> NULL` даёт UNKNOWN, и конъюнкция не становится TRUE.

**Чем `IN` с `NULL` отличается от `OR … IS NULL`?**  
`IN` не заменяет `IS NULL`. Явный `OR name IS NULL` нужен, чтобы вернуть NULL-строки.

**MySQL / Postgres одинаково?**  
По `NULL` в `IN`/`NOT IN`/`!= NULL` — да, трёхзначная логика стандартна. Отличия бывают в других диалектах/`ONLY_FULL_GROUP_BY` и т.п., не здесь.
