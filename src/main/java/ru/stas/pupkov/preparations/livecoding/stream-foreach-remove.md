# Что будет: `list.stream().forEach` + `list.remove`?

## Условие

```java
// 1
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
list.stream().forEach(x -> {
    if (x.equals("C")) {
        list.remove(x);
    }
});

// 2
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
list.stream().forEach(x -> {
    if (x.equals("A")) {
        list.remove(x);
    }
});
```

## Ответ (ArrayList, JDK 11/17/21)

| Случай | Типичный результат |
|--------|--------------------|
| remove `"C"` (последний) | **`ConcurrentModificationException`** |
| remove `"A"` (первый) | **`NullPointerException`** на `x.equals(...)` (часто), либо CME |

Оба варианта — **некорректная** модификация источника во время обхода стрима. Полагаться на «какой именно exception» в проде нельзя; правильный подход — не вызывать `list.remove` из `stream().forEach`.

## Почему так (механика `ArrayListSpliterator`)

`list.stream().forEach(...)` идёт через `ArrayListSpliterator.forEachRemaining`:

1. В начале фиксирует `hi = size` (здесь 3) и `mc = modCount`.
2. Цикл `for (i = 0; i < hi; i++)` читает **`elementData[i]` напрямую**, не сверяясь с актуальным `size`.
3. **После** цикла: если `modCount != mc` → `ConcurrentModificationException`.

### Случай 1 — remove `"C"`

Обход: A → B → C. На C список ужимается, `modCount++`. Цикл уже закончен по `hi==3` → в конце проверка → **CME**.  
(«Тихий» успех здесь не срабатывает: финальная проверка modCount как раз ловит вмешательство.)

### Случай 2 — remove `"A"`

На первом элементе `remove("A")`: массив становится примерно `[B, C, null]`, `size = 2`, но цикл всё ещё идёт до `hi = 3`:

- `i=1` → часто `"C"` (сдвиг, `"B"` можно «пропустить»);
- `i=2` → **`null`** (очищенный хвост ArrayList);
- `null.equals("A")` → **NPE**.

Если бы NPE не случился, в конце цикла всё равно был бы CME по `modCount`.

Итог: fail-fast у spliterator **не полностью** предотвращает «битый» обход — часть действий consumer успевает выполниться (в т.ч. на `null`).

## Как делать правильно

- `list.removeIf(x -> x.equals("A"))`;
- или `Iterator.remove()`;
- или собрать новый список / `stream().filter(...).toList()`.

Не мутировать коллекцию-источник из consumer’а стрима (и обычно не из enhanced-for без `Iterator.remove`).

## Демо

- [StreamForEachRemoveDemo.java](code/StreamForEachRemoveDemo.java)

## Вопросы для собеседования и ответы

**Почему не всегда один и тот же exception?**  
Потому что CME у ArrayList-стрима часто проверяется **в конце** `forEachRemaining`, а цикл уже мог отдать сдвинутые/`null` элементы в lambda.

**Это «гарантия» JLS, что будет NPE при remove первого?**  
Нет. Спецификация стрима запрещает вмешательство в источник; конкретный NPE vs CME — деталь реализации ArrayList. На собесе важно назвать оба риска и сказать «так нельзя».

**А enhanced-for + remove последнего?**  
Отдельная классика про `Iterator`: поведение тоже fail-fast и зависит от момента `next()`/`hasNext()`. К стриму не переносить один в один.
