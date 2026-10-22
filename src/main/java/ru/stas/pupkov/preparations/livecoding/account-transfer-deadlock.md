# Что делает код / что в консоли? (перевод со счетов)

## Условие

```java
private static void move(Account a1, Account a2, int summa) {
    synchronized (a1) {
        synchronized (a2) {
            a1.money -= summa;
            a2.money += summa;
        }
    }
}
// 1000 раз: move(a1,a2,10) и move(a2,a1,10) в пуле 10 потоков
e.shutdown();
if (e.awaitTermination(5, TimeUnit.SECONDS)) {
    System.out.println(a1.money + " " + a2.money);
}
```

## Что делает

Параллельные переводы 10 между двумя счетами в **обе стороны**, с вложенными `synchronized` по объектам счетов.

## Что выведется

Чаще всего **ничего**: через ~5 с `awaitTermination` вернёт `false` (пул не завершился из‑за **deadlock**), `println` не выполнится.

Редко, если дедлока не случилось: `0 0` (симметричные переводы, старт с 0).

## Почему deadlock

- Поток A: `move(a1,a2)` держит `a1`, ждёт `a2`.
- Поток B: `move(a2,a1)` держит `a2`, ждёт `a1`.
- Классический lock-ordering deadlock.

## Как чинить на собесе

Единый порядок блокировок, например по `id.compareTo`:

```java
Account first = a1.id.compareTo(a2.id) < 0 ? a1 : a2;
Account second = first == a1 ? a2 : a1;
synchronized (first) {
    synchronized (second) { ... }
}
```

Или один lock / `ReentrantLock` с `tryLock` + backoff / Actor на счёт.

## Демо

- [AccountTransferDeadlockDemo.java](code/AccountTransferDeadlockDemo.java)

## Вопросы для собеседования и ответы

**Почему не `0 0` гарантированно?**  
Потому что программа часто не доживает до печати из‑за взаимной блокировки.

**`UUID id` зачем в классе?**  
На скрине не используется; как раз удобен для стабильного порядка захвата локов.
