# Что выведет стрим без terminal operation?

## Условие

```java
List<String> animals = List.of("dog", "cat", "parrot");
animals.stream()
    .sorted()
    .peek(item -> System.out.println(item))
    .map(String::length);
System.out.println(animals);
```

## Ответ

```text
[dog, cat, parrot]
```

Только печать исходного списка. `peek` **ничего не печатает**.

## Почему

Промежуточные операции (`sorted`, `peek`, `map`) **ленивые**: без terminal (`forEach`, `collect`, `count`, …) pipeline не выполняется.

`stream()` не меняет `animals` (`List.of` ещё и immutable). Порядок в списке остаётся insertion order, не sorted.

## Демо

- [StreamNoTerminalDemo.java](code/StreamNoTerminalDemo.java)

## Вопросы для собеседования и ответы

**Зачем тогда `peek`?**  
Отладка/side-effect в уже «живом» pipeline с terminal. Без terminal side-effect не случится.

**Если добавить `.toList()` в конец?**  
Сработают sorted → peek (напечатает `cat`, `dog`, `parrot`) → map; `animals` по-прежнему `[dog, cat, parrot]`.
