# Два скрина `Increment`: гонка vs `synchronized(latch)`

## Скрин 1 — без синхронизации

```java
executor.submit(() -> {
    counter1++;
    counter2++;
    latch.countDown();
});
// ...
latch.await();
System.out.println(counter1);
System.out.println(counter2);
```

### Что выведется

Два числа (по строке на счётчик), **недетерминированные**, обычно **меньше 100_000**.  
Могут совпасть или отличаться на немного (lost updates у каждого `++` независимы).

Примеры прогонов: `97394/97394`, `99238/99239`, `98864/98863`.

### Почему

- `counter++` = read + add + write, **не атомарно**.
- 100 потоков, 100_000 задач → data race / lost updates.
- `CountDownLatch` лишь ждёт завершения задач и даёт happens-before на `await()` (main увидит «финальные» записи, которые успели сделать воркеры), но **не защищает** сами инкременты.
- Ожидать ровно `100000` / `100000` нельзя.

## Скрин 2 — что изменилось

Вокруг тела задачи появилось:

```java
executor.submit(() -> synchronized (latch) {
    counter1++;
    counter2++;
    latch.countDown();
});
```

### Эффект

Все инкременты (и `countDown`) сериализованы на одном мониторе — объекте `latch`.  
Гонки нет → стабильный вывод:

```text
100000
100000
```

### Нюансы на собесе

- Синхронизация **на самом `CountDownLatch`** как на lock-объекте — рабочий mutex (внутренности latch на AQS, не на `synchronized(this)` пользователя), но стиль спорный: лучше отдельный `private static final Object LOCK = new Object()` или `AtomicInteger` / `LongAdder`.
- `countDown()` внутри `synchronized` не обязателен для корректности счётчиков (достаточно синхронизировать только `++`), но не ломает latch.
- Альтернативы: `AtomicInteger`, `synchronized` на другом локе, один поток — для демонстрации атомарности.

## Демо

- [IncrementRaceVsSyncDemo.java](code/IncrementRaceVsSyncDemo.java)

## Вопросы для собеседования и ответы

**Почему после `await` не печатается устаревший 0 из‑за visibility?**  
`countDown` happens-before успешный `await`: записи воркера до `countDown` видны main. Проблема скрина 1 — не visibility нулей, а **потерянные обновления** между воркерами.

**Достаточно ли `volatile int counter`?**  
Нет. `volatile` даёт visibility, не атомарность `++`.

**Почему иногда `counter1 == counter2` даже в гонке?**  
Случайность: оба инкремента рядом в одном таске, паттерн потерь похож. Это не гарантия — на другом прогоне разойдутся.
