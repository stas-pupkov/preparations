# Что выведет `StreamApi`? (`peek` + `count` + повтор Stream)

## Условие

Список из 5 `Client` (у двух `city == null`), затем:

1. `stream().peek(set city Москва if null).count()`
2. `validClients = stream().filter(city != null)`
3. `println(count)`, `countBy(validClients, getName)`, `countBy(validClients, getCity)`

`countBy` = `groupingBy(..., TreeMap::new, counting())`.

## Ответ (Java 9+ / 17 / 21)

```text
5
{Иванов=1, Михайлов=1, Сергеев=1}
```

и падение: **`IllegalStateException: stream has already been operated upon or closed`** на втором `countBy`.

## Почему

### 1. `peek` + `count` — side-effect может не выполниться

С Java 9 `Stream.count()` для **SIZED**-стрима (источник `List`) может вернуть размер **без** прохода по pipeline → `peek` **не вызывается**.

Города с `null` остаются `null`. (На Java 8 `peek` обычно успевал отработать.)

`List.of` неизменяем как список, но **элементы** мутабельны — если бы `peek` выполнился, `setCity` сработал бы. Здесь оптимизация важнее.

### 2. `count` = 5

Пять элементов в списке.

### 3. Первый `countBy` по имени

`filter(city != null)` оставляет 3 клиента → TreeMap:

`Иванов=1, Михайлов=1, Сергеев=1` (ключи по natural order).

### 4. Второй `countBy` на том же `validClients`

Stream **одноразовый**. После terminal `collect` повторное использование → `IllegalStateException`.

## Демо

- [StreamApiCountReuseDemo.java](code/StreamApiCountReuseDemo.java)

## Вопросы для собеседования и ответы

**Можно ли полагаться на `peek` для бизнес-логики?**  
Нет. Документация: в основном для отладки; реализации могут элиминировать стадии.

**Как сделать оба `countBy`?**  
Два новых `clients.stream().filter(...)` или один раз собрать `List`/`Map` и считать от него.
