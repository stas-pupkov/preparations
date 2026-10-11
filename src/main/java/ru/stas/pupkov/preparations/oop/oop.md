# ООП (Object-Oriented Programming, объектно-ориентированное программирование)

Java 17+. Заметка для изучения и подготовки к собеседованиям.

## Содержание

1. [Что такое ООП](#1-что-такое-ооп)
2. [Принципы](#2-принципы)
3. [Класс, объект, интерфейс](#3-класс-объект-интерфейс)
4. [is-a и has-a](#4-is-a-и-has-a)
5. [Композиция и агрегация](#5-композиция-и-агрегация)
6. [Binding: early / late](#6-binding-early--late)
7. [Виды полиморфизма в Java](#7-виды-полиморфизма-в-java)
8. [Abstract class vs interface](#8-abstract-class-vs-interface)
9. [Composition over inheritance и LSP](#9-composition-over-inheritance-и-lsp)
10. [Плюсы и минусы ООП](#10-плюсы-и-минусы-ооп)
11. [Демо-код](#11-демо-код)
12. [Вопросы для собеседования и ответы](#12-вопросы-для-собеседования-и-ответы)

---

## 1. Что такое ООП

ООП — подход, в котором программа строится как набор взаимодействующих объектов.
Каждый объект — экземпляр класса. Класс описывает:

- **состояние** — данные объекта (поля);
- **поведение** — операции над этими данными (методы);
- **правила взаимодействия** — что можно вызвать снаружи и какие инварианты обязаны сохраняться.

Объекты обмениваются сообщениями: один объект вызывает метод другого и, при необходимости,
передаёт аргументы. В Java «отправить сообщение» = вызвать метод.

Пример минимальной модели:

```java
final class Account {
    private Money balance;

    Account(Money initial) {
        this.balance = Objects.requireNonNull(initial);
    }

    void deposit(Money amount) {
        balance = balance.plus(amount); // состояние меняется только через поведение
    }

    Money balance() {
        return balance;
    }
}
```

Клиент не знает, как устроен `Money` и как хранится баланс. Он работает с операциями
`deposit` / `balance`. Это и есть объектный стиль: данные и правила рядом, доступ — через
контракт.

Рабочие признаки хорошего ООП-дизайна:

- объекты с осмысленным состоянием и поведением;
- encapsulation (инкапсуляция, сокрытие представления);
- полиморфизм по контракту (часто через интерфейсы);
- наследование типов там, где реально есть отношение подтипа.

Наследование реализации (`extends` класса) — инструмент, а не обязательный каркас.
Глубокие иерархии обычно усложняют чтение и сопровождение.

### Обмен сообщениями

Идея из классического ООП: снаружи нельзя «полезть в поля и переставить биты» — можно только
попросить объект выполнить действие. В Java для обычных переопределяемых instance-методов это
virtual call / dynamic dispatch (виртуальный вызов, динамическая диспетчеризация).
Для `private` / `final` / `static` правила другие — см. [раздел 6](#6-binding-early--late).

---

## 2. Принципы

Обычно выделяют четыре принципа: encapsulation, наследование, полиморфизм, абстракция.
Обмен сообщениями и code reuse (повторное использование кода) — важные следствия, а не
отдельный «обязательный порядок изучения».

### 2.1. Encapsulation

Encapsulation = **связать данные с операциями** и **скрыть представление** за стабильным
контрактом.

Зачем:

1. Клиент зависит от *что делает* объект, а не от *как устроено внутри*.
2. Можно менять реализацию (структуру полей, алгоритм), не ломая клиентов.
3. Инварианты поддерживаются в одном месте (в методах класса), а не размазываются по системе.

Encapsulation **не равна** «все поля `private` + IDE сгенерировала getter/setter
(геттер/сеттер)».

Плохо — представление утекло:

```java
class Order {
    private final List<String> items = new ArrayList<>();

    List<String> getItems() {
        return items; // клиент может сделать getItems().clear()
    }
}
```

Лучше — наружу отдаём immutable (неизменяемый) снимок или работаем операциями:

```java
class Order {
    private final List<String> items = new ArrayList<>();

    void addItem(String item) {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("item");
        }
        items.add(item);
    }

    List<String> items() {
        return List.copyOf(items);
    }
}
```

Границы сокрытия в Java:

| Уровень | Механизм | Пример |
|--------|----------|--------|
| Член класса | `private`, package-private, `protected` | поле `balance` только внутри `Account` |
| Пакет / модуль | package-private типы, JPMS | реализация порта видна только в пакете adapter |
| Объект во времени | immutability, копии, отсутствие setter | `record Money(BigDecimal amount)` |

Полный runnable-пример: [EncapsulationDemo.java](code/EncapsulationDemo.java).

### 2.2. Наследование

Наследование — создание нового типа на основе существующего с заимствованием контракта
и/или реализации.

Два разных смысла:

| Вид | Синтаксис | Что получаем |
|-----|-----------|--------------|
| Наследование реализации | `class B extends A` | поля и методы `A`, можно делать override |
| Наследование типа | `class B implements I` / `interface J extends I` | обязательство соблюдать контракт |

```java
interface PaymentGateway {
    PaymentResult charge(PaymentRequest request);
}

class StripeGateway implements PaymentGateway {
    @Override
    public PaymentResult charge(PaymentRequest request) {
        // HTTP-вызов Stripe
        return PaymentResult.ok(...);
    }
}
```

Здесь наследуется **тип** (роль «шлюз оплаты»). Клиенту важен контракт `PaymentGateway`,
а не то, что внутри — Stripe, эмулятор или stub (заглушка) в тесте.

Наследование реализации уместно, когда:

- между типами есть устойчивое is-a по контракту;
- общая реализация действительно общая, а не «похоже, пригодится»;
- иерархия небольшая и желательно sealed (запечатанная).

Плохой мотив: «хочу переиспользовать два метода из базового класса». Для этого лучше
композиция, utility или delegation (делегирование).

### 2.3. Полиморфизм

Полиморфизм — возможность писать код против общего контракта и подставлять разные реализации.

```java
void notifyUser(Notifier notifier, String message) {
    notifier.send(message); // не знаем, EmailNotifier это или SmsNotifier
}
```

Для **override** (переопределения) конкретный метод выбирается в runtime (во время выполнения)
по типу объекта. Для **overload** (перегрузки) конкретная сигнатура выбирается на
compile-time (этапе компиляции) по типам аргументов.

Подробнее — [раздел 7](#7-виды-полиморфизма-в-java).

### 2.4. Абстракция

Абстракция — выделить в модели то, что важно для задачи, и отсечь детали.

Это приём проектирования. Ключевые слова `abstract` / `interface` — лишь способы
зафиксировать абстракцию в системе типов.

Слабая абстракция (только синтаксис языка):

```java
abstract class Animal {
    abstract void sound();
}
```

Полезная абстракция (граница domain / infrastructure):

```java
interface OutboxWriter {
    void enqueue(OutboxMessage message);
}
```

Клиентский код domain зависит от `OutboxWriter`. За ним может стоять запись в Postgres,
Kafka или in-memory очередь для теста — детали отсечены.

---

## 3. Класс, объект, интерфейс

### Класс

Класс — чертёж типа. Он задаёт:

- какие данные хранит экземпляр;
- какие операции допустимы;
- какие инварианты должны быть истинны после каждой публичной операции.

С точки зрения языка класс — reference type (ссылочный тип). С точки зрения дизайна —
единица модели и модульности.

### Объект

Объект — конкретный экземпляр со своим состоянием.

```java
Account a = new Account(Money.of("100.00"));
Account b = new Account(Money.of("100.00"));
```

`a` и `b` могут быть равны по значению баланса, но это разные объекты
(разная identity, идентичность). В Java identity сравнивают через `==`, а бизнес-равенство —
через `equals`/`hashCode` (если контракт равенства определён).

### Интерфейс: два смысла

Слово «интерфейс» часто смешивают.

1. **API / контракт типа** — что можно делать с объектом и что это значит.
   У обычного класса тоже есть интерфейс: набор его публичных методов и их семантика.
2. **Ключевое слово `interface`** — специальный тип в Java: множественное наследование
   типов, без полей экземпляра (только константы), с возможностью `default` / `static` /
   `private` методов начиная с Java 8.

Пример: у `ArrayList` есть языковые интерфейсы `List`, `Collection`, … и одновременно
свой API-контракт (что делает `add`, что будет с `null`, потокобезопасность и т.д.).

---

## 4. is-a и has-a

is-a («является») и has-a («имеет»):

| Формулировка | Отношение | Воплощение в коде |
|--------------|-----------|-------------------|
| is-a | подтип | `extends` / `implements` |
| has-a | ассоциация | поле, зависимость в конструкторе |

```java
// is-a: Circle является Shape
interface Shape { double area(); }
final class Circle implements Shape { ... }

// has-a: Order имеет список позиций
final class Order {
    private final List<OrderLine> lines;
}
```

is-a проверяют не по словарю предметной области, а по **подстановке**: можно ли объект
подтипа безопасно передать в код, написанный для супертипа (см. LSP ниже).

Геометрически квадрат — прямоугольник. Но если у `Rectangle` контракт «ширину и высоту
можно менять независимо», то `Square extends Rectangle` ломает клиентов. Это уже не is-a
в смысле типов. См. [LspViolationDemo.java](code/LspViolationDemo.java).

---

## 5. Композиция и агрегация

Оба варианта — ассоциация «часть–целое». Разница в **владении lifecycle** (жизненным циклом).

### Композиция

Целое владеет частью. Часть не существует самостоятельно в модели: создаётся вместе с целым
и уходит вместе с ним.

```java
final class Engine {
    private final int horsepower;
    Engine(int horsepower) { this.horsepower = horsepower; }
}

final class Car {
    private final Engine engine; // часть Car

    Car(int horsepower) {
        this.engine = new Engine(horsepower);
    }
}
```

Тот же смысл композиции возможен и при DI (Dependency Injection, внедрении зависимостей):
`Engine` передали в конструктор, но снаружи его больше никто не использует, а отвечает за
него `Car`. Критерий — владение, не место `new`.

### Агрегация

Часть независима: может жить без текущего целого и входить в разные связи.

```java
final class Driver {
    private final String name;
    Driver(String name) { this.name = name; }
}

final class Car {
    private Driver driver; // водитель существует отдельно

    Car(Driver driver) {
        this.driver = driver;
    }

    void assign(Driver driver) {
        this.driver = driver;
    }
}
```

| | Композиция | Агрегация |
|--|------------|-----------|
| Lifecycle | целое владеет частью | часть независима |
| Reuse части | обычно нет | да |
| Типичный код | поле `final`, создаётся/держится внутри | ссылка приходит извне, может меняться |

Практика:

- заменяемые зависимости (repository, client, clock) — агрегация + DI;
- внутренняя деталь, которую не должны видеть снаружи — композиция.

Демо: [CompositionVsAggregationDemo.java](code/CompositionVsAggregationDemo.java).

---

## 6. Binding: early / late

**Binding** (связывание) — момент, когда вызов метода сопоставляется с конкретной реализацией.

### Early binding (раннее связывание, compile-time)

Компилятор знает реализацию по типу **ссылки** (и по сигнатуре):

- `static` методы;
- `private` методы;
- `final` instance-методы;
- overload;
- доступ к полям.

```java
Animal a = new Dog();
a.staticMethod(); // вызовется Animal.staticMethod — тип ссылки Animal
System.out.println(a.kind); // поле тоже по типу ссылки
a.say("hello"); // overload say(String) выбран на compile-time
```

Одноимённый `static` в подклассе — это **hiding** (сокрытие имени), не override.

### Late binding (позднее связывание, runtime)

Для переопределяемых instance-методов JVM выбирает реализацию по типу **объекта**:

```java
Animal a = new Dog();
a.sound(); // Dog.sound
```

Это основа subtype polymorphism.

### Как это устроено в JVM

JLS/JVMS описывают семантику: виртуальный/интерфейсный вызов диспетчеризуется по runtime-типу.
Конкретные механизмы HotSpot — vtable / itable (таблица виртуальных / интерфейсных методов),
inline cache, CHA (Class Hierarchy Analysis), inlining — детали реализации, не часть
спецификации языка. Точнее говорить о dynamic dispatch, а vtable упоминать как типичную
реализацию HotSpot.

Демо: [BindingDemo.java](code/BindingDemo.java).

---

## 7. Виды полиморфизма в Java

Под одним словом «полиморфизм» живут разные механизмы.

### Subtype polymorphism (полиморфизм подтипов)

Общий супертип, разные реализации, выбор в runtime.

```java
interface Painter {
    String draw();
}

Painter shape = new Circle();
shape.draw(); // "circle"
shape = new Square();
shape.draw(); // "square"
```

### Ad hoc polymorphism (специализированный полиморфизм, overload)

Несколько методов с одним именем и разными сигнатурами. Выбор на compile-time.

```java
static String describe(int value) { return "int:" + value; }
static String describe(String value) { return "string:" + value; }

describe(42);     // describe(int)
describe("x");    // describe(String)
```

Важно: overload не смотрит на runtime-тип аргумента, только на тип ссылки при компиляции.

```java
Object value = "hello";
describe(value); // не вызовет describe(String), если есть только describe(int)/describe(String)
                 // — не скомпилируется или уйдёт в более общий overload, если он есть
```

### Parametric polymorphism (параметрический полиморфизм, generics)

Один алгоритм параметризован типом.

```java
static <T> void addTwice(List<? super T> target, T value) {
    target.add(value);
    target.add(value);
}
```

Из-за type erasure (стирания типов) параметр `T` на runtime обычно недоступен как полноценный
reified type (тип, сохранённый во runtime). Поэтому нельзя написать `new T()` и нельзя
корректно сделать `instanceof T` без обходных путей (type token, factory и т.п.).

Демо: [PolymorphismKindsDemo.java](code/PolymorphismKindsDemo.java).

---

## 8. Abstract class vs interface

| Критерий | `abstract class` | `interface` |
|----------|------------------|-------------|
| Наследование | одно | много типов сразу |
| Состояние | поля экземпляра | только константы (`public static final`) |
| Конструктор | да | нет |
| Реализация в типе | обычные и abstract-методы | `default` / `static` / `private` (Java 8+) |
| Модель | семейство с общим каркасом | роль / способность |
| Закрытая иерархия | `sealed` | `sealed` |

### Когда нужен `interface`

Нужна роль, которую реализуют независимые классы:

```java
interface Notifier {
    void send(String message);
}

final class EmailNotifier implements Notifier { ... }
final class SmsNotifier implements Notifier { ... }
```

Один класс может совмещать несколько ролей: `implements Notifier, Auditable`.

### Когда нужен `abstract class`

Нужны общее состояние и/или шаги алгоритма — Template Method (шаблонный метод):

```java
abstract class ReportJob {
    final void run() {
        publish(render(load()));
    }

    protected abstract String load();
    protected abstract String render(String data);

    protected void publish(String rendered) {
        System.out.println(rendered);
    }
}
```

Подклассы заполняют варьируемые шаги, каркас остаётся в базе.

### Java 17+: sealed types

```java
sealed interface Result permits Ok, Error { }
record Ok(String value) implements Result { }
record Error(String code) implements Result { }
```

Компилятор знает полный список подтипов — удобно для исчерпывающего `switch`
(в т.ч. с pattern matching).

Демо: [AbstractVsInterfaceDemo.java](code/AbstractVsInterfaceDemo.java).

---

## 9. Composition over inheritance и LSP

**Composition over inheritance** (предпочитать композицию наследованию) — собирать поведение
из вложенных объектов, а не выстраивать глубокие деревья `extends`, если отношение подтипа
не является настоящим.

### LSP (Liskov Substitution Principle, принцип подстановки Барбары Лисков)

Если код корректен для типа `T`, он должен оставаться корректным для любого подтипа `S`.

Формально удобно думать так:

- подтип **не усиливает** предусловия (не требует «ещё больше», чем база);
- подтип **не ослабляет** постусловия (не обещает «меньше», чем база);
- подтип сохраняет инварианты базы;
- подтип не сюрпризит клиента новой семантикой исключений/побочных эффектов.

Классический контрпример:

```java
void stretch(Rectangle r) {
    r.setWidth(5);
    r.setHeight(4);
    // контракт клиента: area == 20
}
```

Для `Square`, который в каждом setter выравнивает стороны, получится `16`. Клиент,
написанный против `Rectangle`, сломан. Значит, `Square` — плохой подтип `Rectangle`
при таком контракте.

Полный пример: [LspViolationDemo.java](code/LspViolationDemo.java).

Другие типичные нарушения:

- метод базы обещает ненулевой результат, override возвращает `null`;
- override превращается в no-op (пустую операцию), а клиент ждёт эффект;
- подкласс требует обязательный `init()` до любого использования;
- «почти коллекция», которая бросает на `add` после создания, хотя тип обещает `List`.

### Fragile base class (хрупкий базовый класс)

Правка базового класса меняет поведение наследников, даже если их исходники не трогали.
Чем глубже иерархия и чем больше protected-связей, тем выше риск.

Поэтому на практике:

- предпочитают композицию и delegation;
- `extends` оставляют для настоящих подтипов с устойчивым контрактом;
- общие куски выносят в collaborator / package-private helper, а не в God-object
  (божественный объект) базового класса.

Пример замены наследования композицией:

```java
// вместо: class CountingList extends ArrayList<E>
final class CountingList<E> implements List<E> {
    private final List<E> delegate = new ArrayList<>();
    private int addCount;

    @Override
    public boolean add(E e) {
        addCount++;
        return delegate.add(e);
    }
    // остальные методы List — delegation
}
```

Так контролируется, какие методы учитываются в счётчике. При `extends ArrayList` вызов
`addAll` мог обойти переопределённый `add` — классическая ловушка fragile base class / LSP.

### SOLID (кратко в контексте типов)

| Буква | Принцип | Смысл |
|-------|---------|--------|
| S | Single Responsibility (единственной ответственности) | у типа одна причина меняться |
| O | Open/Closed (открытости/закрытости) | новое поведение — новой реализацией, а не правкой всех `switch` |
| L | Liskov Substitution | подтип совместим по контракту |
| I | Interface Segregation (разделения интерфейса) | несколько узких интерфейсов вместо одного толстого |
| D | Dependency Inversion (инверсии зависимостей) | зависеть от абстракций; детали внедрять снаружи |

---

## 10. Плюсы и минусы ООП

### Плюсы

- Domain-инварианты живут рядом с данными (`Account.deposit` не даст уйти в минус, если так задумано).
- Точки расширения через контракты: новая реализация `PaymentGateway` без переписывания use-case
  (сценария использования).
- Локальность: чтобы понять операцию, смотришь класс, а не набор разрозненных процедур.
- Удобные границы для тестирования: подмена интерфейса fake/mock.

### Минусы и цена

- Поток управления при глубоком полиморфизме и наследовании неочевиден («куда реально попали?»).
- Dynamic dispatch и сложные иерархии усложняют рассуждение о коде.
- Соблазн God-object и кустов `Abstract*Support`.
- Anemic domain model (анемичная модель): entity только с полями, вся логика в сервисах —
  классы есть, выгоды ООП слабые.
- Базовые классы часто тащат методы, нужные только части наследников.

В современном Java ООП обычно сочетают с:

- функциональными приёмами (`Function`, streams, immutable данные);
- модульностью пакетов / JPMS;
- архитектурой ports & adapters, где полиморфизм — на границах, а не в каждом DTO
  (Data Transfer Object, объект передачи данных).

---

## 11. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [EncapsulationDemo.java](code/EncapsulationDemo.java) | утечка `List` через getter и безопасный вариант |
| [BindingDemo.java](code/BindingDemo.java) | early/late binding, static, поля, overload |
| [CompositionVsAggregationDemo.java](code/CompositionVsAggregationDemo.java) | владение lifecycle |
| [PolymorphismKindsDemo.java](code/PolymorphismKindsDemo.java) | subtype / ad hoc / parametric |
| [AbstractVsInterfaceDemo.java](code/AbstractVsInterfaceDemo.java) | роль vs Template Method |
| [LspViolationDemo.java](code/LspViolationDemo.java) | Square/Rectangle и контракт |

---

## 12. Вопросы для собеседования и ответы

### 1. Чем encapsulation отличается от «private + getter/setter»?

Encapsulation — это сокрытие представления и защита инвариантов. Модификаторы доступа — только
инструмент. Если у объекта есть setter на все поля и можно собрать недопустимое состояние,
или getter возвращает внутреннюю mutable (изменяемую) коллекцию, сокрытия нет: клиент меняет
устройство объекта напрямую. Нормальный дизайн: осмысленные операции, factory/конструкторы
с проверками, immutability, копии или unmodifiable view наружу.

Пример безопасного API: [EncapsulationDemo.java](code/EncapsulationDemo.java).

### 2. Почему наследование ради code reuse — плохой аргумент?

`extends` приносит не только нужные методы, но и контракт, поля, порядок инициализации,
protected-связность и скрытые инварианты базы. Любая правка базы может сломать наследников
(fragile base class). Если цель — reuse, берут композицию: вложили collaborator и делегировали
ровно те вызовы, которые нужны, под своим контрактом.

### 3. Overload, override и generics — это один полиморфизм?

Нет, это три механизма:

| Механизм | Вид | Когда решается |
|----------|-----|----------------|
| override | subtype | runtime, по типу объекта |
| overload | ad hoc | compile-time, по типам аргументов |
| generics | parametric | один код для разных `T`; на runtime тип обычно стирается |

Путаница дорого стоит: человек ожидает runtime-выбор у overload или считает, что
`List<String>` существует как отдельный класс в JVM.

Демо: [PolymorphismKindsDemo.java](code/PolymorphismKindsDemo.java).

### 4. Почему `static` не полиморфен? Что напечатает вызов через ссылку базового типа?

`static` принадлежит классу, не экземпляру. Binding early — по типу ссылки.
Даже если в подклассе объявлен одноимённый static-метод, это hiding. Пример:

```java
Animal a = new Dog();
a.staticMethod(); // Animal.staticMethod
```

Аналогично с полями: `a.kind` читает поле типа ссылки. Runnable-разбор —
[BindingDemo.java](code/BindingDemo.java).

### 5. В чём разница композиции и агрегации, если оба объекта создаёт Spring?

DI отвечает на вопрос «кто вызывает конструктор», а не «кто владеет смыслом lifecycle».
Если bean `Engine` существует только как внутренность `Car` и никому больше не нужен —
композиция. Если `Driver` (или `PaymentGateway`) разделяется, заменяется, переживает текущего
владельца — агрегация/обычная ассоциация. Смотреть нужно на модель владения и допустимые
lifecycle, а не на наличие `@Bean`.

### 6. Примеры нарушения LSP кроме Square/Rectangle

- Наследник потока, который не поддерживает `mark`/`reset`, хотя клиент писал код против
  контракта, допускающего эти операции.
- Override, возвращающий `null` или глотающий ошибки там, где база обещала результат/исключение.
- Подтип коллекции, запрещающий мутации, но выдающий себя за общий `List`.
- Подкласс сервиса, который падает, пока не вызвали отдельный `init()` — клиент супертипа
  об этом не знает.

Общий паттерн: подтип сюрпризит код, написанный против обещаний супертипа.

### 7. Как HotSpot делает dynamic dispatch и почему vtable — не часть спецификации?

Спецификация задаёт *поведение* `invokevirtual` / `invokeinterface`: выбрать метод исходя из
runtime-типа. HotSpot часто реализует это через vtable/itable и ускоряет через inline cache,
CHA и inlining. Другая JVM теоретически может использовать иной механизм, сохраняя ту же
семантику. Поэтому в объяснении опираются на runtime dispatch, а vtable упоминают как
типичную реализацию HotSpot.

### 8. Когда выбрать `interface`, а когда `abstract class` в Java 17+?

- Несколько независимых ролей, разным классам нужен общий контракт → `interface`.
- Есть общий state и фиксированный каркас алгоритма → `abstract class` (Template Method).
- Иерархия закрыта и должна быть исчерпывающей для `switch` → `sealed`
  (для interface или abstract class).
- `default` methods полезны для эволюции API, но увеличивают «вес» интерфейса и порождают
  конфликты при множественном наследовании.

Демо: [AbstractVsInterfaceDemo.java](code/AbstractVsInterfaceDemo.java).

### 9. Чем опасен getter, возвращающий внутреннюю mutable-коллекцию?

Клиент получает ссылку на внутреннее хранилище и может изменить его в обход проверок:
добавить `null`, удалить всё, сломать порядок. Инварианты класса перестают выполняться,
хотя публичные методы «честные». Лечение: `List.copyOf`, unmodifiable view, отдельный
immutable снимок либо изначально immutable-модель. В JPA entity иногда оставляют
mutable-коллекции из-за persistence/performance — это осознанный trade-off
(компромисс), а не отмена проблемы контракта.

### 10. Чем «интерфейс класса» отличается от ключевого слова `interface`?

«Интерфейс класса» в общем смысле — его публичный контракт: методы, гарантии, протокол
использования (порядок вызовов, потокобезопасность, что считается ошибкой). Ключевое слово
`interface` — конструкция языка для объявления типа без обязательной реализации экземпляра
и для множественного наследования типов. Класс без `implements` всё равно имеет интерфейс
в первом смысле — свой API.

### 11. Можно ли считать дизайн объектно-ориентированным, если почти нет `extends`?

Да. Дизайн на интерфейсах, композиции и защищённом состоянии — обычная и часто предпочтительная
форма ООП в Java. Полиморфизм здесь живёт на контрактах, а не на глубоких деревьях классов.
Наследование реализации нужно только там, где есть настоящее отношение подтипа и выгодный
общий каркас. Глубокие `extends`-иерархии без жёсткого контракта сопровождать сложнее.

### 12. Где ООП мешает в типичном Spring-приложении?

- Иерархии entity/DTO «для красоты», которые мешают эволюции схемы и маппингу.
- Поведение размазано между `@Service`, событиями, base-контроллерами и utility-superclass.
- Неявный поток управления через AOP/reflection, когда полиморфизм уже нельзя проследить чтением кода.
- Попытка завернуть в классы то, что проще выразить чистой функцией без состояния.

Рабочий баланс: в domain — объекты с инвариантами; на границах (controller, messaging adapter) —
тонкие DTO и явные use-case; зависимости — через интерфейсы портов.
