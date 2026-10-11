# Java Core

Java 17+. Заметка для изучения и подготовки к собеседованиям.

Темы JVM (ClassLoader lifecycle, GC, runtime data areas) — в
[`jvm/jvm.md`](../jvm/jvm.md). Дизайн abstract/interface, LSP, binding —
в [`oop/oop.md`](../oop/oop.md). Здесь — язык и стандартная библиотека на уровне
контрактов JLS / java.base.

## Содержание

1. [Платформа и точка входа](#1-платформа-и-точка-входа)
2. [Типы, литералы, операторы, приведение](#2-типы-литералы-операторы-приведение)
3. [Autoboxing и обёртки](#3-autoboxing-и-обёртки)
4. [Модификаторы: access, final, static, abstract](#4-модификаторы-access-final-static-abstract)
5. [Классы, конструкторы, порядок инициализации](#5-классы-конструкторы-порядок-инициализации)
6. [Override, overload, сокрытие static](#6-override-overload-сокрытие-static)
7. [Интерфейсы и абстрактные классы (язык)](#7-интерфейсы-и-абстрактные-классы-язык)
8. [Вложенные классы](#8-вложенные-классы)
9. [Передача параметров и модель памяти (язык)](#9-передача-параметров-и-модель-памяти-язык)
10. [String, пул, конкатенация, switch](#10-string-пул-конкатенация-switch)
11. [Object: equals, hashCode, clone](#11-object-equals-hashcode-clone)
12. [Исключения и try-with-resources](#12-исключения-и-try-with-resources)
13. [Reflection (кратко)](#13-reflection-кратко)
14. [Generics](#14-generics)
15. [Демо-код](#15-демо-код)
16. [Вопросы для собеседования и ответы](#16-вопросы-для-собеседования-и-ответы)

---

## 1. Платформа и точка входа

| Аббревиатура | Роль |
|--------------|------|
| **JVM** (Java Virtual Machine) | Исполняет байт-код, управляет памятью, потоками |
| **JRE** (Java Runtime Environment) | Исторически: JVM + библиотеки для *запуска* |
| **JDK** (Java Development Kit) | Компилятор, инструменты, библиотеки для *разработки* |

С Java 11 отдельно поставляемый «классический» JRE почти исчез из практики: ставят JDK
и при необходимости собирают runtime через `jlink`. На собеседовании достаточно:
JDK ⊃ runtime ⊃ JVM; детали HotSpot — в [`jvm.md`](../jvm/jvm.md).

### `main`

```java
public static void main(String[] args) { }
// эквивалентно для launcher:
public static void main(String... args) { }
```

- Точка входа выбирается при запуске (`java FullyQualifiedName` / манифест JAR).
- В classpath может быть несколько `main` — выполнится тот, который указали.
- Без подходящего `main` класс компилируется, но запуск падает с ошибкой вида
  `Main method not found`.
- Модификаторы `public` + `static` обязательны для стандартного launcher.

### `assert`

```java
void withdraw(Account account, Money amount) {
    // валидация API — всегда, не через assert
    if (amount == null || amount.isNegative()) {
        throw new IllegalArgumentException("amount");
    }
    account.debit(amount);
    // внутренний инвариант после операции — только при -ea
    assert account.balance().isNonNegative() : "balance went negative";
}
```

Включается флагом `-ea` / `-enableassertions`. При `false` — `AssertionError`.
На проде обычно выключен: не заменять им валидацию входных данных API и не делать
side effects внутри выражения assert.

---

## 2. Типы, литералы, операторы, приведение

### Значения по умолчанию

Только для **полей** (instance/static). Локальные переменные default не получают —
компилятор требует definite assignment.

| Тип | Default |
|-----|---------|
| целочисленные (`byte`…`long`) | `0` |
| `float` / `double` | `0.0f` / `0.0d` |
| `char` | `'\u0000'` |
| `boolean` | `false` |
| ссылки | `null` |

```java
class Defaults {
    int count;          // 0
    boolean ready;      // false
    String name;        // null

    void demo() {
        int local;
        // System.out.println(local); // не скомпилируется: definite assignment
    }
}
```

### Литералы

Литерал — явное значение в исходнике. Переменная — не литерал.

```java
int bits = 0b1101_0010;
long id = 42L;
double pi = 3.14;
char c = 'A';
String hello = "Hello";   // "Hello" — литерал; hello — переменная
boolean ok = true;
Object nothing = null;
```

### Логические и побитовые операторы

- `&` / `|` / `^` для `boolean` — без short-circuit: оба операнда всегда считаются.
- `&&` / `||` — short-circuit: правая часть может не выполниться.
- Побитовые: `~`, `&`, `|`, `^`, `<<`, `>>` (знаковый), `>>>` (беззнаковый сдвиг).

```java
String s = null;
// безопасно: правая часть не вызовется
if (s != null && s.length() > 0) { /* ... */ }

// & вычислит оба операнда → NPE
// if (s != null & s.length() > 0) { }

int flags = 0b1010;
int masked = flags & 0b0010; // 0b0010
int shifted = flags << 1;    // 0b10100
```

Тернарный оператор: оба ветки — выражения совместимого типа.

```java
int abs = n >= 0 ? n : -n;
Number value = flag ? Integer.valueOf(1) : Double.valueOf(1.0); // lub → Number
```

### Приведение типов (casting)

| Вид | Пример | Автоматически? |
|-----|--------|----------------|
| Widening primitive | `int` → `long` | да |
| Narrowing primitive | `long` → `int` | только явно; биты обрезаются |
| Widening reference | `Dog` → `Animal` | да |
| Narrowing reference | `Animal` → `Dog` | явно; иначе `ClassCastException` |

```java
int i = 1_000_000;
long wide = i;          // widening
byte narrow = (byte) i; // narrowing: старшие биты отброшены

Animal a = new Dog();   // widening reference
Dog d = (Dog) a;        // narrowing reference, ok
// Cat c = (Cat) a;     // ClassCastException

Object obj = "text";
if (obj instanceof String s) { // pattern matching, Java 16+
    return s.length();
}

byte b1 = 1, b2 = 2;
// byte sum = b1 + b2;  // ошибка: byte + byte → int
byte sum = (byte) (b1 + b2);
```

---

## 3. Autoboxing и обёртки

Autoboxing — неявное `int` → `Integer` (и обратно, unboxing) при присваивании и
вызовах методов.

```java
Integer boxed = 42;          // Integer.valueOf(42)
int raw = boxed;             // boxed.intValue()

List<Integer> list = new ArrayList<>();
list.add(1);                 // boxing
int first = list.get(0);     // unboxing
```

Ловушки production:

```java
Integer a = 127;
Integer b = 127;
System.out.println(a == b);       // true — cache [-128, 127]

Integer c = 128;
Integer d = 128;
System.out.println(c == d);       // false — разные объекты
System.out.println(c.equals(d));  // true

Integer x = null;
// int y = x;                     // NullPointerException при unboxing

byte b = 1;
// Short s = b;                   // не скомпилируется: нет неявного byte → Short
Short s = (short) b;              // ok
```

Верх кэша `Integer` расширяется `-XX:AutoBoxCacheMax`. В hot path boxing даёт GC pressure —
для чисел смотреть `IntStream` / примитивные коллекции при реальной необходимости.

Демо: [AutoboxingCacheDemo.java](code/AutoboxingCacheDemo.java).

---

## 4. Модификаторы: access, final, static, abstract

### Access

| Модификатор | Класс | Пакет | Подкласс (др. пакет) | Все |
|-------------|:-----:|:-----:|:--------------------:|:---:|
| `private` | ✓ | | | |
| package-private (default) | ✓ | ✓ | | |
| `protected` | ✓ | ✓ | ✓ | |
| `public` | ✓ | ✓ | ✓ | ✓ |

При override нельзя **сузить** видимость. Расширить — можно.

```java
class Parent {
    protected void save() { }
}

class Child extends Parent {
    @Override
    public void save() { }   // расширили — ok
    // @Override
    // private void save() { } // сузили — ошибка компиляции
}
```

`private` доступен внутри класса и вложенным (nestmates, Java 11+).

### `final`

```java
final class Money {                 // нельзя extends
    private final BigDecimal amount; // blank final → только в ctor/init

    Money(BigDecimal amount) {
        this.amount = Objects.requireNonNull(amount);
    }
}

final List<String> items = new ArrayList<>();
items.add("a");                     // ok: ссылка та же
// items = List.of();               // нельзя: переприсвоить final-ссылку
```

`final` на ссылке ≠ immutability объекта.

### `static`

```java
class Counter {
    static int created;     // одно на Class
    int id;

    Counter() {
        id = ++created;
    }

    static int total() {
        return created;
        // return id;       // нет this — ошибка
    }
}

Counter.total();            // обращение через тип
```

### `abstract`

```java
abstract class Shape {
    abstract double area();           // без тела

    double perimeterToAreaRatio() {   // общая реализация
        return perimeter() / area();
    }

    abstract double perimeter();
}

// abstract + static / final / private — запрещены
```

---

## 5. Классы, конструкторы, порядок инициализации

### Виды типов (практическая таксономия)

- top-level: обычный / `abstract` / `final`;
- `interface`, `enum`, `record` (14/16), `annotation`;
- nested: static nested, inner, local, anonymous;
- `sealed` + `permits` (17) — закрытая иерархия.

### Конструкторы

```java
final class User {
    private final String id;
    private final String email;

    User(String id, String email) {              // parameterized
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
    }

    User(User other) {                           // copy constructor
        this(other.id, other.email);
    }

    static User of(String id, String email) {    // factory поверх private/packaged ctor
        return new User(id, email);
    }
}

final class Utils {
    private Utils() { }                          // non-instantiable
}
```

- **Default constructor** — генерируется, только если своих конструкторов нет.
- `private` ctor: utility, controlled factory, доступ для nested.

### Порядок инициализации

```java
class Parent {
    static { System.out.println("1 Parent.<clinit>"); }
    { System.out.println("3 Parent instance"); }
    Parent() { System.out.println("4 Parent ctor"); }
}

class Child extends Parent {
    static { System.out.println("2 Child.<clinit>"); }
    { System.out.println("5 Child instance"); }
    Child() { System.out.println("6 Child ctor"); }
}

// new Child() → 1, 2, 3, 4, 5, 6  (static — один раз при первой инициализации класса)
```

Ошибка в static init → `ExceptionInInitializerError`; дальше часто `NoClassDefFoundError`.
Ошибка в instance init — исключение из конструктора, объект «недосоздан».

Демо: [ClassInitOrderDemo.java](code/ClassInitOrderDemo.java).

---

## 6. Override, overload, сокрытие static

| | Override | Overload | Static same name |
|--|----------|----------|------------------|
| Когда выбирается | runtime (тип объекта) | compile-time (типы аргументов) | compile-time (тип ссылки) |
| Сигнатура | та же (covariant return ок) | другое число/типы параметров | та же — это **hiding**, не override |
| `@Override` | ловит ошибки | не применим | не должен «проходить» как override |

```java
class Animal {
    Animal self() { return this; }           // covariant target
    void speak() { System.out.println("..."); }
    static String kind() { return "animal"; }
    void log(String msg) { }
    void log(String msg, Throwable t) { }    // overload
}

class Dog extends Animal {
    @Override
    Dog self() { return this; }              // covariant return

    @Override
    void speak() { System.out.println("woof"); }

    static String kind() { return "dog"; }   // hiding, не override

    // @Override void log(int code) { }      // это overload — @Override не встанет
}

Animal a = new Dog();
a.speak();          // woof — runtime тип
System.out.println(a.kind());     // "animal" — тип ссылки
System.out.println(Dog.kind());   // "dog"

class Cat extends Animal {
    @Override
    void speak() {
        super.speak();
        System.out.println("meow");
    }
}
```

Правила override: visibility не сужать; return — тот же или covariant; смена параметров →
overload; checked `throws` можно сузить/убрать, нельзя добавить несовместимое checked.

Демо механики binding: [`BindingDemo.java`](../oop/code/BindingDemo.java).

---

## 7. Интерфейсы и абстрактные классы (язык)

См. также дизайн-разбор: [`oop.md` §8](../oop/oop.md#8-абстрактный-класс-и-интерфейс).

```java
public interface Repository<T> {
    T findById(String id);                   // public abstract

    default T require(String id) {           // default — эволюция API
        T value = findById(id);
        if (value == null) {
            throw new NoSuchElementException(id);
        }
        return value;
    }

    static <T> Repository<T> empty() {       // static
        return id -> null;
    }

    private void trace(String id) {          // private, Java 9+ — factoring
        // ...
    }

    int MAX_PAGE = 100;                      // public static final
}

sealed interface Result permits Ok, Err { }
record Ok(String value) implements Result { }
record Err(String message) implements Result { }
```

| Член | Модификаторы по умолчанию / правила |
|------|-------------------------------------|
| abstract method | `public abstract` |
| `default` method | `public`, тело обязательно |
| `static` method | `public` (или `private` с 9) |
| `private` method (9+) | для factoring default/static |
| поле | `public static final` |

Нельзя объявить abstract-метод интерфейса `final`. Маркеры (`Serializable`, `Cloneable`)
остаются в JDK; в новом коде чаще annotations.

---

## 8. Вложенные классы

| Вид | Ссылка на outer instance | Когда |
|-----|--------------------------|-------|
| Static nested | нет | helper, не нужен outer |
| Inner (member) | скрытый `this$0` | нужен доступ к состоянию outer |
| Local | как у enclosing context | одноразовый тип внутри блока |
| Anonymous | как у enclosing | одно место, часто → lambda |

```java
class Outer {
    private int state = 1;

    static class StaticNested {
        void show() {
            // System.out.println(state); // нет Outer.this
        }
    }

    class Inner {
        void show() {
            System.out.println(Outer.this.state); // есть this$0
        }
    }

    Runnable localDemo(String prefix) {
        // prefix — effectively final
        class Local implements Runnable {
            @Override
            public void run() {
                System.out.println(prefix + state);
            }
        }
        return new Local();
    }

    Runnable anonymousDemo() {
        return () -> System.out.println(state); // вместо anonymous class
    }
}
```

С Java 16+ inner может объявлять static-члены. Утечка: опубликовали `Inner` → живёт весь
`Outer`. Предпочитать static nested.

Демо: [NestedClassesCaptureDemo.java](code/NestedClassesCaptureDemo.java).

---

## 9. Передача параметров и модель памяти (язык)

### Pass-by-value

В Java **всегда** передача по значению: копируется содержимое слота (примитив или ссылка).

```java
void reassign(List<String> list) {
    list = new ArrayList<>();   // меняет только локальную копию ссылки
    list.add("x");
}

void mutate(List<String> list) {
    list.add("x");              // видно вызывающему
}

List<String> xs = new ArrayList<>();
reassign(xs);
System.out.println(xs);         // []
mutate(xs);
System.out.println(xs);         // [x]
```

Демо: [PassByValueDemo.java](code/PassByValueDemo.java).

### Heap / Stack (уровень языка)

```java
void frame() {
    int local = 42;             // примитив в кадре (стек потока)
    Point p = new Point(1, 2);  // ссылка p в кадре; объект Point — в heap
    // поле p.x лежит в объекте в heap, не «в стеке»
}
```

- Объекты и массивы — в heap.
- Поле-примитив объекта лежит **в объекте в heap**.
- Утверждение «примитивы всегда в стеке» — ложно.

GC, Metaspace, коллекторы — [`jvm.md` §5, §8](../jvm/jvm.md).
`finalize()` — deprecated for removal; ресурсы — try-with-resources / `Cleaner`.

`final` / `finally` / `finalize()` — три разных механизма (модификатор / блок / метод Object).

---

## 10. String, пул, конкатенация, switch

### Свойства `String`

- `final` класс, содержимое immutable (с Java 9 — compact strings: Latin-1 / UTF-16 в `byte[]`).
- `hashCode` кэшируется после первого вычисления — удобный ключ `HashMap`.
- Сравнение: `==` — identity; `equals` — содержимое (и только оно нужно в бизнес-коде).

```java
String a = "java";
String b = "java";
String c = new String("java");

System.out.println(a == b);              // true — один pooled литерал
System.out.println(a == c);              // false — отдельный объект в heap
System.out.println(a == c.intern());     // true — intern вернул pooled
System.out.println(a.equals(c));         // true
```

| Тип | Mutable | Thread-safe | Когда |
|-----|---------|-------------|-------|
| `String` | нет | да (immutability) | значение, ключ, API |
| `StringBuilder` | да | нет | сборка в одном потоке |
| `StringBuffer` | да | синхронизирован | редко; legacy |

### Где хранится string pool

«Пул строк» — это не отдельный регион вроде Eden/Old. В HotSpot это связка:

1. **StringTable** — внутренняя хеш-таблица JVM: ключ ≈ содержимое, значение → ссылка
   на канонический `String`.
2. **Объекты `String`**, на которые указывает таблица, — обычные Java-объекты.

| Версия HotSpot | Где живут интернированные `String` | Примечание |
|----------------|-------------------------------------|------------|
| Java ≤ 6 | **PermGen** | OOM: `PermGen space` при раздуве пула |
| Java 7+ | **Java heap** | GC собирает недостижимые interned-строки |
| Java 8+ | heap; PermGen удалён | **Metaspace ≠ string pool** (там metadata классов) |

Для Java 17 на собесе достаточно: *пул в heap с Java 7; Metaspace тут ни при чём*.
Уточнение senior-уровня: сама StringTable — структура VM (не «ещё один `HashMap` в
heap»); **полезный груз** — объекты `String` в heap. Лимит/давление — через heap и
размер таблицы (`-XX:StringTableSize=` — степень двойки, размер buckets; смотреть
доки конкретной JDK).

Связь с classfile: литералы лежат в **constant pool** класса (в `.class` / runtime
constant pool). При первом разрешении `ldc` JVM кладёт/находит соответствующий
`String` в string pool. Constant pool класса и string pool — разные вещи: первый
принадлежит типу (metadata + resolved entries), второй — глобальная дедупликация
строк процесса.

### Как строка попадает в пул

| Способ | Попадает в pool? |
|--------|------------------|
| Литерал `"abc"` | да (при resolve) |
| Compile-time константа `"a" + "b"` | да (constant folding → один литерал) |
| Runtime `"a" + x` / `StringBuilder` | нет (новый объект), пока не `intern()` |
| `new String("abc")` | литерал `"abc"` в pool есть; **новый** объект — отдельно в heap |
| `s.intern()` | да: вернёт уже лежащий или зарегистрирует текущий / эквивалент |

```java
String lit = "hello";
String heap = new String("hello");     // лишний объект; "hello" уже в pool
String runtime = "hel" + someVar;      // обычно не из pool
String folded = "hel" + "lo";          // == lit (constant expression)

lit == heap;                 // false
lit == heap.intern();        // true
lit == runtime;              // часто false
lit == runtime.intern();     // true, если содержимое совпало
```

`intern()`: если в таблице уже есть равная по содержимому строка — вернуть **её**;
иначе зарегистрировать и вернуть каноническую ссылку. Контракт — про identity для
равного содержимого среди интернированных, не про то, что «все строки программы в pool».

### Конкатенация

```java
// Java 9+: часто invokedynamic → StringConcatFactory, не явный StringBuilder в bytecode
String s = "user=" + id + ", status=" + status;

// явный builder — когда много дописываний в цикле / условная сборка
StringBuilder sb = new StringBuilder(64);
for (String part : parts) {
    sb.append(part);
}
```

Не путать с pool: результат `+` / `toString()` — обычный heap-объект, если только
выражение не свернулось в литерал на этапе компиляции.

### GC, deduplication, риски `intern()`

- С Java 7 interned-строки **могут быть собраны**, если на них нет сильных ссылок
  (и запись ушла из StringTable) — в отличие от мифа «раз попало в pool, навсегда».
- G1 **string deduplication** (`-XX:+UseStringDeduplication`) — другое: шарит
  *underlying `byte[]`* у разных `String` с одинаковым содержимым, **не** делает
  `==` true и не заменяет `intern()`.
- Злоупотребление `intern()` на пользовательском вводе → рост heap + contention/
  раздув StringTable. Имеет смысл для длинноживущего ограниченного словаря
  (протокольные константы, интернирование символов в компиляторах и т.п.), не как
  «оптимизация всех ключей».

### Пароль: `char[]` vs `String`

```java
char[] password = {'s', 'e', 'c', 'r', 'e', 't'};
try {
    authenticate(password);
} finally {
    Arrays.fill(password, '\0'); // затираем; со String так нельзя
}
```

`String` нельзя надёжно затереть; литерал/интернирование удлиняют жизнь в heap dump.
`char[]` снижает окно утечки, но не даёт криптографической гарантии сам по себе.

### `switch` по `String`

```java
String status = "ok";
String label = switch (status) {          // switch expression, Java 14+
    case "ok", "OK" -> "success";
    case "fail" -> "error";
    default -> "unknown";
};

// switch (null) → NullPointerException до выбора case
// сравнение case — через equals (регистр важен), не через ==
```

Демо: [StringInternPoolDemo.java](code/StringInternPoolDemo.java),
[SwitchOnStringDemo.java](code/SwitchOnStringDemo.java).

---

## 11. Object: equals, hashCode, clone

### `==` vs `equals`

```java
String x = new String("a");
String y = new String("a");
System.out.println(x == y);       // false — разные объекты
System.out.println(x.equals(y));  // true — эквивалентность содержимого
```

### Контракт `equals` + `hashCode`

```java
final class Point {
    private final int x;
    private final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y; // те же поля, что в equals
    }
}

// Ошибка: overload вместо override — коллекции не вызовут
// public boolean equals(Point that) { ... }
```

`instanceof` в `equals` допускает подклассы → риск сломать симметрию. Для value-типов
чаще `final` + `getClass()` или `record`.

```java
record PointR(int x, int y) { } // equals/hashCode/toString сгенерированы
```

Демо: [EqualsHashCodeContractDemo.java](code/EqualsHashCodeContractDemo.java).

### Клонирование

```java
final class Order {
    private final List<String> items;

    Order(List<String> items) {
        this.items = List.copyOf(items);
    }

    Order(Order other) {                    // предпочтительнее Cloneable
        this.items = List.copyOf(other.items); // deep по смыслу содержимого
    }
}
```

`Object.clone()` + `Cloneable` — shallow copy без конструктора; хрупкий API.

---

## 12. Исключения и try-with-resources

```
Throwable
├── Error          // обычно не ловить: OOM, StackOverflow, …
└── Exception
    ├── RuntimeException   // unchecked
    └── остальные          // checked
```

```java
class OrderNotFoundException extends RuntimeException { // unchecked domain
    OrderNotFoundException(String id) {
        super("order not found: " + id);
    }
}

void read(Path path) throws IOException {              // checked в сигнатуре
    throw new FileNotFoundException(path.toString());
}

try {
    read(Path.of("missing.txt"));
} catch (FileNotFoundException e) {                    // сначала специфичное
    log.warn("missing", e);
} catch (IOException e) {
    log.error("io", e);
} catch (IllegalArgumentException | IllegalStateException e) { // multi-catch
    log.error("illegal", e);
}
```

### `finally` и try-with-resources

```java
int bad() {
    try {
        return 1;
    } finally {
        return 2; // перекрывает 1 — антипаттерн
    }
}

try (InputStream in = Files.newInputStream(path);
     BufferedInputStream bin = new BufferedInputStream(in)) {
    return bin.readAllBytes();
} // close в обратном порядке; ошибки close → suppressed
```

`finally` не выполнится при `System.exit` / `halt` / крахе JVM. Ресурс — `AutoCloseable`.

OOM: `Java heap space`, `Metaspace` (не PermGen), `GC overhead limit exceeded` —
детали в [`jvm.md`](../jvm/jvm.md).

Демо: [ExceptionFinallyTwrsDemo.java](code/ExceptionFinallyTwrsDemo.java).

---

## 13. Reflection (кратко)

```java
Class<?> type = Class.forName("com.example.Service");
Object instance = type.getDeclaredConstructor().newInstance();
Method method = type.getMethod("execute", String.class);
method.invoke(instance, "payload");

Field field = type.getDeclaredField("secret");
field.setAccessible(true); // на JDK 17+ может бросить InaccessibleObjectException
Object value = field.get(instance);
```

Цена: потеря compile-time проверок, module boundaries, обычно медленнее прямого вызова
(MethodHandles сглаживают часть кейсов). ClassLoader — [`jvm.md` §3–4](../jvm/jvm.md).

---

## 14. Generics

Generics (обобщения) — параметрический полиморфизм на этапе компиляции: один код
работает с разными типами аргументов, а проверки типов делает `javac`, а не касты
в runtime. Введены в Java 5; реализация — **type erasure** (JLS §4.6): в байткоде
остаются raw-типы, границы и вставленные cast’ы.

### 14.1. Синтаксис: типы и методы

```java
// generic type
final class Box<T> {
    private T value;
    T get() { return value; }
    void set(T value) { this.value = value; }
}

// несколько параметров + bounds
interface Repository<ID, T extends Entity<ID>> {
    Optional<T> findById(ID id);
}

// generic method — параметр объявляется перед return type
static <T> T first(List<T> list) {
    return list.isEmpty() ? null : list.get(0);
}

// constructor
class Pair<A, B> {
    Pair(A a, B b) { }
}
```

Diamond (`<>`, Java 7+): `Map<String, List<Integer>> m = new HashMap<>();`.
С Java 9 — diamond и в anonymous class при выводимом контексте (ограниченно).

### 14.2. Variance: ковариантность, контравариантность, инвариантность

**Variance** (вариантность) — как ведут себя *составные* типы, когда меняется аргумент
типа. Это не про сами `Dog` / `Animal`, а про контейнер: наследует ли он отношение
подтипов своих элементов.

Пусть `Dog` <: `Animal` (`Dog` — подтип `Animal`). Вопрос: что с `Box<Dog>` и
`Box<Animal>`?

| Термин | Если `Dog` <: `Animal`, то… | Смысл |
|--------|-----------------------------|--------|
| **Ковариантны** (covariant) | `Box<Dog>` <: `Box<Animal>` | направление подтипов **сохраняется** |
| **Контравариантны** (contravariant) | `Box<Animal>` <: `Box<Dog>` | направление **переворачивается** |
| **Инвариантны** (invariant) | ни то, ни другое | `Box<Dog>` и `Box<Animal>` несовместимы |

#### Массивы — ковариантны

```java
Dog[] dogs = new Dog[1];
Animal[] animals = dogs;   // ok: Dog[] <: Animal[]
animals[0] = new Cat();    // компилируется → ArrayStoreException в runtime
```

Ковариантность удобна для **чтения** (`Animal a = animals[0]`), но опасна для
**записи**: через «широкую» ссылку можно положить несовместимый элемент. JVM ловит
это проверкой при store (`ArrayStoreException`).

#### Generics — инвариантны

```java
List<Dog> dogs = new ArrayList<>();
// List<Animal> animals = dogs; // ошибка компиляции

List<String> strings = new ArrayList<>();
// List<Object> objects = strings; // тоже нельзя
```

Иначе через `List<Animal>` / `List<Object>` в «список собак/строк» положили бы
`Cat` / `Integer`. У `ArrayList` нет store-check по type argument — поломка ушла бы
в поздний `ClassCastException` при чтении. Поэтому параметризованные типы инвариантны:
`List<String>` **не** подтип `List<Object>`.

#### Wildcards — локальная variance

Гибкость API дают wildcards, а не наследование `List<String>` от `List<Object>`:

| Запись | Variance | Практика |
|--------|----------|----------|
| `List<? extends Animal>` | ковариантный взгляд | producer: читаем как `Animal`, почти нельзя писать |
| `List<? super Dog>` | контравариантный взгляд | consumer: пишем `Dog`, читаем безопасно как `Object` |
| `List<Animal>` | инвариантный | и читать, и писать `Animal` |

Подробнее — PECS в [§14.5](#145-wildcards-и-pecs).

### 14.3. Type erasure

Правила стирания (упрощённо):

| Исходный тип | После erasure |
|--------------|---------------|
| `List<String>` | `List` |
| `T` без bound | `Object` |
| `T extends Number` | `Number` |
| `T extends A & B` | leftmost bound `A` |
| `T[]` | `|T|[]` |

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass()); // true — один ArrayList.class

// нельзя:
// if (list instanceof List<String>) { }
// List<String>.class
// new T();  new T[];
```

Компилятор вставляет cast’ы на чтении:

```java
String s = list.get(0);
// ≈ String s = (String) list.get(0);
```

Следствия для практики:

- overload по одним только type arguments невозможен:
  `void f(List<String>)` и `void f(List<Integer>)` — одна сигнатура после erasure;
- в static-контексте нет `T` экземпляра: `static T field` запрещён;
- exception не может быть generic (`class MyEx<T> extends Exception` — нельзя);
- reflection видит raw / `ParameterizedType` у *полей/методов* через
  `getGenericReturnType()`, но не «живое» `T` у произвольного экземпляра без токена.

### 14.4. Bounds

```java
// upper bound
<T extends Number> double sum(List<T> values) {
    return values.stream().mapToDouble(Number::doubleValue).sum();
}

// intersection type (несколько границ)
<T extends Number & Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}

// lower bound — только у wildcard (? super T), не у type parameter класса
```

Upper bound сужает erasure: методы `Number` доступны на `T` без лишнего cast.

### 14.5. Wildcards и PECS

Wildcards задают variance «на месте использования» (см. [§14.2](#142-variance-ковариантность-контравариантность-инвариантность)):
`? extends` — ковариантность (удобно читать), `? super` — контравариантность (удобно писать).

| Wildcard | Variance | Читать как | Что можно читать | Что можно писать |
|----------|----------|------------|------------------|------------------|
| `?` | unknown | unknown | `Object` | почти ничего (кроме `null`) |
| `? extends T` | covariant | producer | `T` (или subtype) | почти ничего |
| `? super T` | contravariant | consumer | `Object` | `T` (и subtype) |

**PECS** (Producer Extends, Consumer Super) — мнемоника Bloch:

```java
// src — производит Number'ы → extends
// dst — потребляет Number'ы → super
static void copy(List<? extends Number> src, List<? super Number> dst) {
    for (Number n : src) {
        dst.add(n);
    }
}

List<Integer> ints = List.of(1, 2);
List<Object> objects = new ArrayList<>();
copy(ints, objects); // ok

List<? extends Number> producer = ints;
Number n = producer.get(0);   // ok
// producer.add(1);           // нельзя: неизвестен точный subtype

List<? super Integer> consumer = objects;
consumer.add(1);              // ok
Object o = consumer.get(0);   // читать безопасно только как Object
```

Типичные API: `Collections.copy`, `Comparator`, `Stream.map` / collectors.

Unbounded `List<?>` полезен, когда метод только читает структуру (`size`, `isEmpty`)
или передаёт список дальше без записи элементов известного типа.

### 14.6. Capture conversion

Компилятор «захватывает» `?` во временную type variable. Поэтому нельзя просто
присвоить элемент из `List<?>` обратно в тот же список как конкретный тип без helper’а:

```java
static void swapFirstTwo(List<?> list) {
    // list.set(0, list.get(1)); // ошибка: capture mismatch
    swapHelper(list);
}

private static <T> void swapHelper(List<T> list) {
    T tmp = list.get(0);
    list.set(0, list.get(1));
    list.set(1, tmp);
}
```

Паттерн: wildcard снаружи → private generic method внутри (capture helper).

### 14.7. Raw types и heap pollution

Raw type (`List` без аргументов) — legacy / escape hatch. Присваивание raw →
parameterized даёт unchecked warning и открывает **heap pollution**: в
`List<String>` оказывается не-`String`, падение откладывается до чтения + cast.

```java
List raw = new ArrayList();
raw.add(42);
List<String> strings = raw;           // unchecked conversion
// String s = strings.get(0);         // ClassCastException здесь
```

`@SafeVarargs` / `@SuppressWarnings("unchecked")` — только когда инвариант реально
сохранён (типично: final/`private` метод, не утекает массив varargs наружу).

### 14.8. Reifiable types, массивы, varargs

**Reifiable** — тип, полностью доступный в runtime (примитивы, non-generic классы,
raw, unbounded wildcards вроде `List<?>`, массивы reifiable-компонент).
`List<String>` — **не** reifiable → нельзя `instanceof List<String>`, нельзя
`new List<String>[]` (generic array creation).

```java
// List<String>[] arrays = new List<String>[10]; // ошибка

@SafeVarargs
static <T> void printAll(T... items) { // T... ≈ T[] — потенциальный pollution
    for (T item : items) {
        System.out.println(item);
    }
}
```

Обход для «массива generic»: `List<List<String>>` или `toArray(IntFunction)` /
`list.toArray(String[]::new)`.

### 14.9. Bridge methods

После erasure override должен совпасть по raw-сигнатуре. Компилятор генерирует
synthetic **bridge**:

```java
class Node<T> {
    public void setData(T data) { }
}

class IntNode extends Node<Integer> {
    @Override
    public void setData(Integer data) { } // + bridge setData(Object) → cast + delegate
}
```

В stack trace иногда видно `...$bridge` / synthetic. На собесе: «как JVM сохраняет
полиморфизм generic override при erasure».

### 14.10. Как «протащить» тип в runtime

Erasure не даёт `T.class`. Рабочие приёмы:

```java
// 1) Class-токен
static <T> T parse(String json, Class<T> type) {
    return mapper.readValue(json, type);
}

// 2) super-type-token (TypeReference / ParameterizedTypeReference)
new TypeReference<List<User>>() { }; // анонимный subclass сохраняет generic signature

// 3) factory / Supplier<T>
static <T> List<T> nCopies(int n, Supplier<T> factory) {
    return Stream.generate(factory).limit(n).toList();
}
```

Spring/`ObjectMapper` / Guava `TypeToken` строятся на втором приёме.

### 14.11. Ограничения и типичные ошибки

| Запрещено / опасно | Почему |
|--------------------|--------|
| `new T()`, `new T[]` | erasure |
| `instanceof T`, `T.class` | не reifiable |
| generic exception | JVM catch по классу |
| static поле типа `T` | нет экземпляра type arg |
| overload только по type args | одна erasure-сигнатура |
| `equals(T)` вместо `equals(Object)` | не override (см. §11) |
| возвращать `T[]` из varargs наружу | heap pollution |

Inference (Java 8+ targets / poly expressions) обычно «просто работает»; если ломается —
явно указать `<String>method(...)` или промежуточную переменную с типом.

Демо: [GenericsPecsErasureDemo.java](code/GenericsPecsErasureDemo.java).

---

## 15. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [EqualsHashCodeContractDemo.java](code/EqualsHashCodeContractDemo.java) | overload `equals`, equals без hashCode, getClass vs instanceof |
| [StringInternPoolDemo.java](code/StringInternPoolDemo.java) | литерал / `new` / `intern` / `==` |
| [PassByValueDemo.java](code/PassByValueDemo.java) | reassign параметра vs мутация объекта |
| [NestedClassesCaptureDemo.java](code/NestedClassesCaptureDemo.java) | static nested vs inner, effectively final |
| [ClassInitOrderDemo.java](code/ClassInitOrderDemo.java) | порядок init, `ExceptionInInitializerError` |
| [AutoboxingCacheDemo.java](code/AutoboxingCacheDemo.java) | Integer cache, `==`, NPE unboxing |
| [ExceptionFinallyTwrsDemo.java](code/ExceptionFinallyTwrsDemo.java) | finally+return, multi-catch order, suppressed |
| [SwitchOnStringDemo.java](code/SwitchOnStringDemo.java) | case-sensitive, `switch(null)`, expression |
| [GenericsPecsErasureDemo.java](code/GenericsPecsErasureDemo.java) | invariance, PECS, erasure, heap pollution, capture helper |

---

## 16. Вопросы для собеседования и ответы

### 1. JDK, JRE и JVM — что говорить на Java 17?

JVM исполняет байт-код. Runtime = JVM + стандартные библиотеки. JDK = инструменты
разработки (`javac`, `javadoc`, `jlink`, …) плюс тот же runtime. Отдельный классический
JRE почти не ставят: берут JDK и при необходимости собирают урезанный image. Модули
JPMS меняют картину classpath/`rt.jar`, но тройка ролей остаётся.

### 2. Почему «примитивы в стеке, объекты в куче» — неточная формула?

Локальные примитивы и ссылки живут в кадре метода (стек потока). Сами объекты — в heap.
Поле `int` у объекта лежит вместе с объектом в heap. Стек хранит ссылку на объект, а не
«все примитивы программы».

```java
class Holder {
    int field; // в heap, внутри экземпляра Holder
}
void m() {
    int local = 1;           // кадр метода
    Holder h = new Holder(); // ссылка в кадре, объект в heap
}
```

### 3. Передача параметров: по значению или по ссылке?

Строго по значению. Для ссылочных типов копируется **ссылка**. Метод не может заставить
переменную вызывающего указывать на другой объект через присваивание параметру, но может
изменить состояние объекта, на который ссылка указывает. Демо: [PassByValueDemo.java](code/PassByValueDemo.java).

### 4. Чем override отличается от сокрытия static-метода?

Override — выбор реализации по runtime-типу объекта (`invokevirtual`). Одноимённый
`static` в подклассе — hiding: вызывается метод, выбранный по типу ссылки на этапе
компиляции. `@Override` на static не должен маскировать ошибку; обращаться к static
лучше через имя класса.

```java
Animal a = new Dog();
a.speak();     // override → Dog
Animal.kind(); // явно через класс; a.kind() всё равно Animal.kind при типе Animal
```

### 5. Что сломается, если переопределить `equals` и забыть `hashCode`?

Контракт: равные объекты → одинаковый hash. `HashMap`/`HashSet` сначала кладут/ищут по
бакету hash, потом по `equals`. При identity-hashCode равные по `equals` ключи попадают
в разные бакеты → «положил — не нашёл». Демо: [EqualsHashCodeContractDemo.java](code/EqualsHashCodeContractDemo.java).

### 6. `getClass() ==` vs `instanceof` в `equals`?

`instanceof` истинен для подклассов → легко нарушить симметрию, если подкласс добавляет
поля. `getClass()` запрещает равенство с подклассом — проще для value-типов, хуже для
иерархий, где равенство по общим полям задумано. Для не-наследуемых типов / `final` /
`record` обычно берут точное сравнение класса; для открытых иерархий — отдельный дизайн.

### 7. Почему `equals(MyClass)` вместо `equals(Object)` — баг?

Это overload, не override. `Object.equals` остаётся identity. `HashSet.contains` вызывает
`equals(Object)` → ваш метод не участвует. Ловится `@Override` на правильной сигнатуре.

```java
// плохо — не override
public boolean equals(Point other) { return ...; }

// правильно
@Override
public boolean equals(Object other) { return ...; }
```

### 8. Где string pool и когда звать `intern()`?

С Java 7 интернированные `String` живут в **heap**; Metaspace — не пул строк
(это после удаления PermGen, где пул был ≤ Java 6). Механика HotSpot: StringTable
(таблица VM) → ссылки на канонические объекты в heap. Литералы и compile-time
константы попадают в pool при resolve; `new String("a")` и runtime-конкатенация —
отдельные объекты, пока не `intern()`. `intern()` нужен редко (ограниченный
долгоживущий словарь). На пользовательском вводе — риск раздуть heap/таблицу.
G1 string deduplication ≠ `intern()` (шаринг `byte[]`, не identity).
Демо: [StringInternPoolDemo.java](code/StringInternPoolDemo.java).

### 9. Порядок инициализации класса и экземпляра?

Сначала `<clinit>` иерархии (static поля/блоки от Object-ward к классу). При `new` —
`super` ctor chain, затем instance-поля/блоки, затем тело конструктора. Ошибка в static →
`ExceptionInInitializerError`, класс остаётся broken. Демо: [ClassInitOrderDemo.java](code/ClassInitOrderDemo.java).

### 10. Когда `finally` не выполнится? Что с `return` в `finally`?

Не выполнится при `System.exit`/`Runtime.halt`, крахе JVM, если поток умер/завис до входа
в `finally`. `return`/`throw` в `finally` маскирует исход из `try` — считать дефектом.
Для ресурсов предпочитать try-with-resources: [ExceptionFinallyTwrsDemo.java](code/ExceptionFinallyTwrsDemo.java).

### 11. Чем inner class опаснее static nested?

Inner держит скрытую ссылку на outer (`this$0`). Утечка inner (слушатель, callback,
кэш) удерживает весь outer. Static nested этой ссылки не имеет. С Java 16 inner может
иметь static-члены; effectively final достаточно для захвата локальных переменных.

### 12. Почему `Integer a = 127; Integer b = 127; a == b` часто true, а для 128 — false?

Autoboxing идёт через `Integer.valueOf`, который кэширует [-128, 127] по умолчанию.
Внутри диапазона — один объект; снаружи — новые. Сравнивать значения: `equals` или
unbox в `int`. Демо: [AutoboxingCacheDemo.java](code/AutoboxingCacheDemo.java).

### 13. Зачем `finalize` не использовать в новом коде?

Нет гарантий сроков вызова; Finalizer-поток сериализует финализацию; исключения глотаются;
объект переживает как минимум один GC cycle; API deprecated for removal. Ресурсы —
`AutoCloseable` + TWR; редкий cleanup вне TWR — `java.lang.ref.Cleaner`.

### 14. Checked vs unchecked: когда что выбирать в API?

Checked — когда вызывающий **обязан** осознанно обработать ожидаемую неудачу (часто I/O
на границе системы) и восстановление реально. Unchecked — нарушения контракта, баги,
состояния, которые слою выше редко осмысленно чинят. Тащить checked через все слои
Spring-сервиса часто шумит API; типичный компромисс — translation в domain/unchecked
на границе.

```java
// граница I/O — checked ок
Order load(Path path) throws IOException { ... }

// use-case — unchecked domain
Order getOrder(String id) {
    return repo.find(id).orElseThrow(() -> new OrderNotFoundException(id));
}
```

### 15. Почему `StringBuilder` предпочтительнее `StringBuffer` почти всегда?

Оба mutable. `StringBuffer` синхронизирует методы — лишний cost, если builder локален
для потока (обычный случай). Для shared mutable-строки синхронизация на уровне
`StringBuffer` всё равно слабая модель; нужна явная синхронизация/другая структура.
Современный код: `StringBuilder` или чистый `String` + concat.

### 16. Почему `List<String>` нельзя передать туда, где ждут `List<Object>`? Что такое PECS?

Параметризованные типы **инвариантны** (см. §14.2): иначе через `List<Object>` в
«список строк» положили бы `Integer`. Массивы при этом **ковариантны**
(`String[]` <: `Object[]`) — ценой `ArrayStoreException` при записи. Для гибкости
generic API используют wildcards: `? extends T` — ковариантный producer (читаем как
`T`); `? super T` — контравариантный consumer (пишем `T`). Это и есть PECS.
Пример: `copy(List<? extends Number> src, List<? super Number> dst)`.
Демо: [GenericsPecsErasureDemo.java](code/GenericsPecsErasureDemo.java).

### 17. Что остаётся после type erasure и зачем bridge methods?

`List<String>` и `List<Integer>` в runtime — один raw `List` (часто один и тот же
`ArrayList.class`). Параметр `T` стирается до границы или `Object`; компилятор вставляет
cast’ы. Если подкласс уточняет параметр (`Node<Integer>.setData(Integer)`), после erasure
сигнатура не совпала бы с `setData(Object)` предка — компилятор генерирует synthetic
bridge `setData(Object)`, который кастует и делегирует. Так сохраняется override при
полиморфном вызове.
