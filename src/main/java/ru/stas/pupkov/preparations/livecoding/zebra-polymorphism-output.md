# Что выведет: `Mammal m = new Zebra()`?

## Условие

`Zebra extends Mammal`: поле `name` скрыто, `makeNoise()` переопределён, `foo()` static скрыт.

```java
Mammal m = new Zebra();
System.out.println(m.name + m.makeNoise() + m.foo());

Zebra z = new Zebra();
System.out.println(z.name + z.makeNoise() + z.foo());
```

## Ответ

```text
furry bray foo 
stripes bray baz 
```

## Почему

| Вызов | Правило | Результат |
|-------|---------|-----------|
| `m.name` | поле — по **типу ссылки** (`Mammal`) | `furry ` |
| `m.makeNoise()` | instance — **runtime** (`Zebra`) | `bray ` |
| `m.foo()` | static — по типу ссылки (`Mammal`) | `foo ` |
| `z.name` / `z.makeNoise()` / `z.foo()` | всё через `Zebra` | `stripes ` + `bray ` + `baz ` |

Поля и static **не полиморфны** (hiding). Переопределяются только instance-методы.

## Демо

- [ZebraPolymorphismOutputDemo.java](code/ZebraPolymorphismOutputDemo.java)

## Вопросы для собеседования и ответы

**Как достучаться до `Zebra.name` через `Mammal m`?**  
Cast: `((Zebra) m).name`. Иначе только поле родителя.

**Стоит ли hiding static / полей?**  
Плохая практика; путает читателя. Static лучше звать как `Mammal.foo()` / `Zebra.foo()`.
