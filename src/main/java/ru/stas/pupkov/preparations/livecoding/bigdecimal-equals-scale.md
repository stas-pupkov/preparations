# Почему `BigDecimal("10.0").equals(BigDecimal("10.00"))` → `false`?

## Условие

```java
new BigDecimal("10.0").equals(new BigDecimal("10.00"))
```

## Ответ

Да, **`false`**. Числа численно равны, но `BigDecimal.equals` сравнивает **value + scale**, не только математическое значение.

| | `"10.0"` | `"10.00"` |
|--|----------|-----------|
| unscaled | 100 | 1000 |
| scale | 1 | 2 |

`equals` требует совпадения обоих → `false`.  
`compareTo` смотрит на численное равенство → **`0`** (равны).

## Следствия

- `HashSet` / `HashMap` с ключами `BigDecimal`: `10.0` и `10.00` — **разные** ключи.
- Для «одинаковости денег» обычно: `compareTo == 0`, или привести scale (`setScale`, `stripTrailingZeros` — осторожно с hash/представлением).

Контракт: если `compareTo == 0`, **не** обязаны `equals == true` — осознанное исключение из «обычного» equals/compareTo согласованного стиля.

## Демо

- [BigDecimalEqualsScaleDemo.java](code/BigDecimalEqualsScaleDemo.java)

## Вопросы для собеседования и ответы

**Как правильно сравнивать суммы?**  
`a.compareTo(b) == 0` (или единый scale в домене). Не `equals`, если scale может отличаться.

**`stripTrailingZeros` спасёт equals?**  
`10.0` и `10.00` после strip могут стать равны по equals, но `1.0` → unscaled `1` scale `0`, а ещё есть сюрпризы с научной записью/`toString`. Лучше явная политика scale в приложении.
