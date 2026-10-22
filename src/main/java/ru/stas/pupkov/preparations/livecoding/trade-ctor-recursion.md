# Выполнится ли `main` без ошибок? (`Trade` ↔ `TradeCancel`)

## Условие

```java
@Getter
public class TradeProc {
    public static void main(String[] args) {
        Trade trade = new Trade(BigDecimal.valueOf(1234.78), LocalDate.now());
        TradeCancel tradeCancel = trade.getCancel();
    }
}

class Trade {
    private BigDecimal cost;
    private LocalDate date;
    private TradeCancel cancel;

    public Trade(BigDecimal cost, LocalDate date) {
        this.cost = cost;
        this.date = date;
        cancel = new TradeCancel(cost, date);
    }
}

class TradeCancel {
    private BigDecimal cost;
    private LocalDate date;
    private final Trade primaryTrade;

    public TradeCancel(BigDecimal cost, LocalDate date) {
        this.cost = cost.negate();
        this.date = date.plusDays(1);
        primaryTrade = new Trade(cost, date);
    }
}
```

## Ответ

**Нет.**

### 1. Компиляция (как на скрине)

`@Getter` висит на `TradeProc` (полей нет) → для `Trade` **не** генерируется `getCancel()`.  
Вызов `trade.getCancel()` → **ошибка компиляции**.

### 2. Если геттер есть / дошли до runtime

Взаимное создание в конструкторах:

`new Trade` → `new TradeCancel` → `new Trade` → … → **`StackOverflowError`**.

До строки с `getCancel()` выполнение обычно не доходит.

## Как чинить на собесе

Не строить граф объектов «друг друга» внутри конструкторов:

- создавать `TradeCancel` снаружи и передавать уже готовый `Trade` (`primaryTrade = trade`);
- или lazy-init / фабрика;
- цикл ссылок после полной инициализации обоих объектов допустим, рекурсия в ctor — нет.

## Демо

- [TradeCtorRecursionDemo.java](code/TradeCtorRecursionDemo.java) — явный getter, ловим `StackOverflowError`.

## Вопросы для собеседования и ответы

**Это взаимная ссылка полей = утечка / незавершаемый GC?**  
Нет, вопрос про **рекурсию конструкторов**, не про GC. После `StackOverflow` объекты даже не создаются стабильно.

**Почему `@Getter` на `TradeProc` не помогает?**  
Lombok генерирует геттеры для полей **того** класса, где стоит аннотация.
