# SOLID: буква O на примере `DiscountService`

## Условие

«Принцип Solid: объясни букву O» + код:

```java
public class DiscountService {
    public double calculate(Client client, double summ) {
        if (client.getType().equals("Обычный")) {
            return summ * 0.95; // 5%
        } else if (client.getType().equals("ВИП")) {
            return summ * 0.80; // 20%
        } else if (client.getType().equals("Сотрудник")) {
            return summ * 0.50; // 50%
        }
    }
}
```

## Буква O — Open-Closed Principle (OCP)

Сущности должны быть **открыты для расширения** и **закрыты для модификации**: новое поведение добавляем, не правя раз за разом уже отлаженный код (в идеале).

## Как код нарушает O

Новый тип клиента («Партнёр», «Студент») → править `DiscountService`: ещё один `else if`, риск сломать существующие ветки, регрессии, раздувание метода.

Тип зашит **строками** + ветвление в одном месте = классический smell против OCP (и рядом с Switch/If по типу).

## Как объяснить «правильно» на собесе

1. Сформулировать OCP.  
2. Показать, что здесь расширение = правка центрального сервиса.  
3. Предложить расширение через полиморфизм / стратегию:

- `DiscountPolicy` / `ClientType` с методом `apply(sum)`;
- или map `type → strategy`, регистрация новых политик без правки `calculate`;
- в Spring — список бинов `DiscountRule`, выбор по типу.

Пример идеи:

```java
public interface DiscountPolicy {
    boolean supports(Client client);
    BigDecimal apply(BigDecimal sum);
}

public class DiscountService {
    private final List<DiscountPolicy> policies;

    public BigDecimal calculate(Client client, BigDecimal sum) {
        return policies.stream()
                .filter(p -> p.supports(client))
                .findFirst()
                .orElse(fullPrice())
                .apply(sum);
    }
}
```

Новый тариф = **новый класс** (+ регистрация), `DiscountService` не трогаем.

OCP не абсолютен: иногда правка одного `switch` проще кучи иерархий; принцип — про стоимость изменений в растущей системе.

## Побочные проблемы в сниппете (если спросят)

- Нет `return` / `else` → код **не компилируется** (не все пути возвращают `double`).
- `double` для денег — лучше `BigDecimal`.
- Сравнение строк / опечатки в `"ВИП"`; лучше enum или полиморфный тип клиента.

## Демо

- [SolidODiscountDemo.java](code/SolidODiscountDemo.java) — if-else vs стратегии.

## Вопросы для собеседования и ответы

**OCP = «никогда не менять классы»?**  
Нет. Закрыты для модификации *в том смысле*, что типичное расширение не требует лезть в стабильное ядро. Багфиксы и осознанный рефакторинг — норма.

**Чем отличается от Strategy?**  
Strategy — один из способов соблюсти OCP для взаимозаменяемого поведения. OCP шире (модули, плагины, полиморфизм).

**Когда if по типу допустим?**  
Закрытый набор из 2–3 вариантов, редко меняется, команда маленькая. Как только типы плодятся — выносить расширение.
