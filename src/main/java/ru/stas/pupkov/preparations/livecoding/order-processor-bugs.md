# Проблемы в `OrderProcessor` (code review)

## Условие

```java
public class OrderProcessor {

    private List<String> processedOrders = new ArrayList<>();

    public double calculateTotal(List<Double> prices, double discountPercent) {
        double total = 0;
        for (int i = 0; i <= prices.size(); i++) {
            total += prices.get(i);
        }
        total = total + (total * discountPercent / 100);
        return total;
    }

    public void processOrders(List<String> orders) {
        if (orders.size() == 0) {
            System.out.println("No orders");
        }
        for (String order : orders) {
            if (order.startsWith("TEST")) {
                orders.remove(order);
            }
        }
        processedOrders = orders;
    }

    public String findOrder(List<String> orders, String id) {
        for (String order : orders) {
            if (order == id) {
                return order;
            }
        }
        return null;
    }
}
```

## Проблемы по методам

### `calculateTotal`

1. **Off-by-one:** `i <= prices.size()` → на последней итерации `IndexOutOfBoundsException` (валидные индексы `0 .. size-1`). Нужно `i < prices.size()` или for-each / stream.
2. **«Скидка» увеличивает сумму:** `total + total * percent/100` — это надбавка (как налог), не скидка. Для скидки: `total * (1 - discountPercent/100)` (с проверкой диапазона 0..100).
3. **`double` для денег** — лучше `BigDecimal` / minor units.
4. **NPE**, если `prices == null`; нет защиты от `null` в элементах списка (`Double`).

### `processOrders`

1. **`ConcurrentModificationException`:** `orders.remove(order)` во время enhanced-for по той же коллекции (fail-fast iterator). Правильно: `removeIf(o -> o.startsWith("TEST"))`, `Iterator.remove()`, или новый список.
2. **Пустой список:** печатает `"No orders"`, но **не return** — дальше цикл и присвоение всё равно идут (для empty это безопасно, но ветка бессмысленна / вводит в заблуждение).
3. **`order.startsWith` при `order == null`** → NPE.
4. **`processedOrders = orders`:** сохраняется **ссылка** на входной список, не копия. Внешние изменения `orders` меняют состояние процессора; наоборот, процессор мутирует чужой список. Лучше `new ArrayList<>(filtered)` / unmodifiable copy.
5. Мутация аргумента — неочевидный API side-effect.

### `findOrder`

1. **`order == id`:** сравнение ссылок, не содержимого → ложные «не найдено». Нужно `Objects.equals(order, id)` / `id.equals(order)` с политикой на null.
2. Возврат `null` вместо `Optional` — классический NPE-риск у вызывающего.

## Сводно (что сказать на собесе)

| Приоритет | Баг |
|-----------|-----|
| Высокий | `<= size` → IOOBE |
| Высокий | remove в for-each → CME |
| Высокий | `==` для String |
| Средний | скидка как надбавка |
| Средний | aliasing `processedOrders = orders` |
| Ниже | double/деньги, null-safety, пустой early-return |

## Демо

- [OrderProcessorBugsDemo.java](code/OrderProcessorBugsDemo.java) — воспроизведение IOOBE / CME / `==` vs `equals` и исправленные варианты.

## Вопросы для собеседования и ответы

**Почему CME, а не «тихо пропуск»?**  
Fail-fast iterator `ArrayList` сверяет `modCount`; structural modify без `Iterator.remove` → CME.

**Копия списка vs та же ссылка?**  
Защита инкапсуляции и предсказуемость состояния; иначе баги «кто-то снаружи почистил orders».
