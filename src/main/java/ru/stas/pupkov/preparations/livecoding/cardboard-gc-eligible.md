# Сколько объектов eligible for GC к `// doStuff`? (`CardBoard`)

## Условие

```java
class CardBoard {
    Short story = 200;

    CardBoard go(CardBoard cb) {
        cb = null;
        return cb;
    }

    public static void main(String[] args) {
        CardBoard c1 = new CardBoard();
        CardBoard c2 = new CardBoard();
        CardBoard c3 = c1.go(c2);
        c1 = null;
        // doStuff
    }
}
```

## Ответ

**2 объекта**

1. `CardBoard`, на который раньше ссылался `c1`
2. его поле `Short story` (значение 200)

## Почему

| Ссылка | После `doStuff` |
|--------|------------------|
| `c1` | `null` → первый `CardBoard` (+ его `Short`) unreachable |
| `c2` | всё ещё указывает на второй `CardBoard` (+ его `Short`) — **живы** |
| `c3` | `null` (`go` вернул `null`) |

`go(c2)`: в Java передаётся **копия ссылки**. `cb = null` внутри метода **не** обнуляет `c2`.

`Short story = 200`: автобоксинг `Short.valueOf(200)`. Кэш `Short` — только **[-128; 127]**, значит 200 — отдельный объект на куче (не pooled). Вместе с «осиротевшим» `CardBoard` уходит и этот `Short`.

Итого eligible: **2**. Не 1 (забыли `Short`) и не 3 (`c2` ещё держит второй комплект).

## Демо

- [CardBoardGcEligibleDemo.java](code/CardBoardGcEligibleDemo.java) — разбор ссылок + замечание про кэш `Short`.

## Вопросы для собеседования и ответы

**Если было бы `story = 100`?**  
`Short` из кэша может разделяться; тогда «отдельный» Short на инстанс не гарантирован, и ответ «2» уже спорнее. В задаче специально **200**.

**Меняет ли `c3 = c1.go(c2)` достижимость `c2`?**  
Нет. Параметр — локальная копия; возврат `null` только в `c3`.
