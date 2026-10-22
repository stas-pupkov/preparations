# Собеседование: `SaveDataUseCase` + SQL top-3 + unique strings

Три задачи с одного скрина.

---

## 1. Проблемы в `SaveDataUseCase`

### Условие

```java
class SaveDataUseCase {

    @Transactional()
    void saveSomeData() {
        var entity = saveStatusPending(); // insert PROCESSING

        Boolean isSuccess = true;
        try {
            saveA();
            saveB();
            saveC();
        } catch (Exception ex) {
            isSuccess = false;
        }

        return updateStatusSuccessOrFailed(entity, isSuccess); // SUCCESS/FAILED
    }
}
```

### Главная проблема: исключение проглочено → tx **закоммитится**

`@Transactional` по умолчанию помечает rollback, только если из метода **вылетает** runtime-исключение (или Error).

Здесь `catch (Exception)` **без rethrow**:

- Spring считает метод успешным → **commit**;
- уже выполненные `saveA` (и частично дальше) **остаются** в БД;
- статус ставят в `FAILED`, хотя данные A/B могут быть частично записаны.

Итог: бизнес ожидает «упало = откатилось / только FAILED», фактически — **FAILED + мусор от успешных шагов**.

Это классический антипаттерн «флаг вместо rollback».

### Остальные проблемы

1. **`void` + `return updateStatus...`** — не компилируется (или сигнатура врёт).
2. **Нет логирования / метрик** в `catch` — ошибка теряется.
3. **Широкий `catch (Exception)`** — глотает и баги, и checked; нет различия retryable / fatal.
4. **Одна tx на pending + A/B/C + update статуса** — снаружи не видно `PROCESSING` до commit. Часто pending в `REQUIRES_NEW`.
5. **Идемпотентность** не проговорена: повтор после FAILED может дублировать A/B.
6. **Самовызов** без прокси — `@Transactional` может не примениться.

### Как чинить на собесе

**A. Всё или ничего** — не глотать исключение / явный rollback рабочей tx.

**B. Нужен статус FAILED без частичных A/B:**

```text
tx1 REQUIRES_NEW: insert PROCESSING
try {
  tx2: saveA/B/C   // при ошибке rollback tx2
  REQUIRES_NEW: SUCCESS
} catch {
  REQUIRES_NEW: FAILED
  log + metrics
}
```

**C.** Компенсации / outbox, если A/B/C не только БД.

### Вопросы (tx)

**Почему FAILED, а данные A уже в БД?**  
Исключение не вышло из `@Transactional`-метода → commit всей tx.

**Достаточно ли `setRollbackOnly()` в catch?**  
Откатит и update статуса в той же tx. Для «сохранить FAILED» нужна другая tx (`REQUIRES_NEW`).

---

## 2. SQL: top-3 пользователей по сумме заказов за 30 дней

### Условие

Таблицы: `users(id, name)`, `orders(id, user_id, amount, created_at)`.  
Вывести топ-3 пользователей с наибольшей суммой заказов за последние 30 дней: имя и сумма.

### Решение (PostgreSQL / стандартный SQL)

```sql
SELECT u.name,
       SUM(o.amount) AS total_amount
FROM users u
JOIN orders o ON o.user_id = u.id
WHERE o.created_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
GROUP BY u.id, u.name
ORDER BY total_amount DESC
LIMIT 3;
```

Нюансы на собесе:

- `GROUP BY u.id, u.name` (или только `u.id`, если `name` функционально зависит от `id` — в Postgres при PK так можно `GROUP BY u.id`).
- Пользователи **без** заказов за период в top не попадут (`JOIN`; для «с нулём» — `LEFT JOIN` + `COALESCE`, но для top-3 по сумме обычно inner).
- Индекс под фильтр/агрегацию: `(created_at)`, часто `(user_id, created_at)` или `(created_at, user_id)`.
- Таймзона / `CURRENT_DATE` vs `CURRENT_TIMESTAMP` — уточнить у интервьюера.
- При ничьих на 3-м месте `LIMIT 3` обрежет произвольно; иногда просят `DENSE_RANK()` / `WITH TIES` (Postgres: `FETCH FIRST 3 WITH TIES`).

### Демо

- [TopUsersByOrdersDemo.java](code/TopUsersByOrdersDemo.java) — H2 с тем же смыслом запроса.

---

## 3. Unique strings: длина ↑, при равенстве лексикографически

### Условие

Метод: список строк → уникальные, сортировка по длине asc, при равной длине — lexicographically.  

Вход: `["apple", "banana", "kiwi", "apple", "fig", "grape"]`  
Выход: `["fig", "kiwi", "apple", "grape", "banana"]`

### Решение

```java
static List<String> uniqueSorted(List<String> input) {
    return input.stream()
            .distinct()
            .sorted(Comparator
                    .comparingInt(String::length)
                    .thenComparing(Comparator.naturalOrder()))
            .toList();
}
```

Порядок: fig(3) → kiwi(4) → apple(5), grape(5) → banana(6).  
`distinct()` на Stream для `String` — по `equals`; порядок первого вхождения для distinct сохраняется до `sorted`, затем полная пересортировка.

Альтернатива: `new TreeSet<>(comparator)` + `addAll` — uniqueness + sort за один проход по компаратору (но TreeSet не использует insertion order; для этой задачи компаратор задаёт полный порядок, ок). Важно: если две разные строки сравнить компаратором в 0 — одна потеряется; здесь length+lex гарантирует 0 только для равных строк.

### Демо

- [UniqueStringsByLengthDemo.java](code/UniqueStringsByLengthDemo.java)

### Вопросы

**`distinct()` до или после `sorted`?**  
Для результата с полной сортировкой всё равно; `distinct` раньше чуть меньше работы на sort. На параллельном стриме — осторожнее с ordered distinct.

**Locale для lexicographic?**  
`String` natural order — UTF-16 code units / `Comparable`, не коллация ICU. Для «алфавита» UI иногда нужен `Collator`.
