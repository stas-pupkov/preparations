# Что вернёт метод / что в консоли? (`OutOfMemoryError`)

## Условие

```java
private static int exceptionTest() {
    int count = 1000;
    try {
        while (true) {
            Object[][] array = new Object[count][count];
            count *= 100;
        }
    } catch (OutOfMemoryError e) {
        System.out.println("OutOfMemory");
    } catch (Exception ex) {
        System.out.println("Exception");
    }
    count /= 100;
    System.out.println("Максимальный размер массива " + count);
    try {
        RuntimeException[][] array = new RuntimeException[count][count];
        array[0][1] = new NullPointerException();
    } catch (Exception ex) {
        System.out.println("Exception 2");
    } catch (Error e) {
        System.out.println("Error");
    }
    return count;
}
```

## Ответ (типичный heap)

**Консоль:**

```text
OutOfMemory
Максимальный размер массива 1000
```

**return:** `1000`

Второго блока catch-сообщений нет.

## Разбор

### Первый try

1. `count = 1000` → `new Object[1000][1000]` обычно ок → `count = 100_000`.
2. `new Object[100000][100000]` → **`OutOfMemoryError`** (до `count *= 100`).
3. `OutOfMemoryError` ⊂ `Error`, не `Exception` → ветка `"OutOfMemory"`, не `"Exception"`.
4. `count` на момент OOM = `100_000` → `count /= 100` → **1000**.
5. Печать максимального размера.

Точное число зависит от `-Xmx` (если уже `[1000][1000]` не влезает — после `/100` будет `10`). На обычных кучах для этой задачи — **1000**.

### Второй try

- `RuntimeException[1000][1000]` при том же heap, где уже проходил `Object[1000][1000]`, обычно ок.
- `array[0][1] = new NullPointerException()` — NPE **является** `RuntimeException` → присваивание законно, исключения нет.
- `"Exception 2"` / `"Error"` не печатаются.

## Ловушки на собесе

- Ловить `OutOfMemoryError` в проде почти всегда плохая идея.
- `count *= 100` несколько раз может дать **overflow** `int` → `NegativeArraySizeException` (`RuntimeException`) → тогда `"Exception"`, другой `count`. До overflow обычно успевает OOM.
- После OOM JVM может быть в плохом состоянии; второй alloc теоретически тоже может кинуть `Error`.

## Демо

- [OomExceptionTestDemo.java](code/OomExceptionTestDemo.java) — лучше с `-Xmx64m`.

## Вопросы для собеседования и ответы

**Почему не сработал `catch (Exception)` на OOM?**  
`OutOfMemoryError` наследует `Error` / `Throwable`, не `Exception`.

**Почему NPE в массиве `RuntimeException[][]` не ловится?**  
Потому что не бросается: объект кладётся в ячейку совместимого типа.
