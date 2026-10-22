# Что выведется: try-with-resources + исключение в `close`?

## Условие

```java
private static void exception() {
    try (AutoCloseable a = createResources()) {
        throw new IllegalArgumentException("Исключение в try");
    } catch (Exception e) {
        System.out.println(e.getMessage());
        System.out.println("Перехватили исключение");
    }
}

private static AutoCloseable createResources() {
    return () -> {
        System.out.println("Начинаем закрывать ресурс");
        throw new IOException("Exception");
    };
}
```

## Ответ

```text
Начинаем закрывать ресурс
Исключение в try
Перехватили исключение
```

## Почему

Порядок try-with-resources при исключении в теле:

1. Бросается primary: `IllegalArgumentException("Исключение в try")`.
2. Вызывается `close()` у ресурса → печать `"Начинаем закрывать ресурс"`, затем `IOException`.
3. Исключение из `close` **не затирает** primary, а кладётся в **suppressed** (`Throwable.addSuppressed`).
4. В `catch` приходит primary → `getMessage()` = `"Исключение в try"`.
5. Сообщение из `IOException` через `getMessage()` **не** видно (только `e.getSuppressed()`).

Если бы в `try` исключения не было, а упал только `close`, в `catch` была бы уже `IOException`.

## Демо

- [TryWithResourcesSuppressedDemo.java](code/TryWithResourcesSuppressedDemo.java)

## Вопросы для собеседования и ответы

**Куда девается `IOException` из `close`?**  
В массив suppressed у primary. Смотреть: `e.getSuppressed()`.

**Несколько ресурсов в `try (...)`?**  
`close` в обратном порядке объявления; каждое исключение при close может стать suppressed к primary (или к уже выбранному исключению закрытия — по правилам JLS).
