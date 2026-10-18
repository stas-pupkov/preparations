# Java 8 (язык и стандартная библиотека)

Java 17+. Заметка для изучения и подготовки к собеседованиям.
Фокус — возможности, пришедшие в Java 8, с актуализацией под современный toolchain
(что устарело, что осталось контрактом платформы).

## Содержание

1. [Что принесла Java 8](#1-что-принесла-java-8)
2. [Лямбда-выражения](#2-лямбда-выражения)
3. [Захват переменных](#3-захват-переменных)
4. [Ссылки на методы и конструкторы](#4-ссылки-на-методы-и-конструкторы)
5. [Функциональные интерфейсы](#5-функциональные-интерфейсы)
6. [Default и static методы в интерфейсах](#6-default-и-static-методы-в-интерфейсах)
7. [Optional](#7-optional)
8. [Stream API](#8-stream-api)
9. [Параллельные стримы](#9-параллельные-стримы)
10. [Новые методы Map](#10-новые-методы-map)
11. [Date/Time API (`java.time`)](#11-datetime-api-javatime)
12. [Повторяемые аннотации и прочее](#12-повторяемые-аннотации-и-прочее)
13. [Демо-код](#13-демо-код)
14. [Вопросы для собеседования и ответы](#14-вопросы-для-собеседования-и-ответы)

---

## 1. Что принесла Java 8

Ключевые изменения **языка и API**, которые до сих пор определяют стиль Java-кода:

| Область | Нововведение |
|---------|--------------|
| Язык | лямбды, method/constructor references, `default`/`static` в `interface` |
| Типы | функциональные интерфейсы + `@FunctionalInterface` |
| Коллекции | Stream API, новые default-методы `Collection`/`List`/`Map` |
| Null-модель | `Optional` |
| Время | `java.time` (JSR-310) вместо `Date`/`Calendar` как основного API |
| Аннотации | `@Repeatable`, type annotations (JSR-308) |
| Утилиты | `Base64`, `StringJoiner`, `Arrays.parallelSort` |
| JVM (JDK 8) | Metaspace вместо PermGen, профили Compact Profiles |

Что из «списка Java 8» важно знать на собесе, но **уже не использовать в новом коде**:

- **Nashorn** (`jjs`) — удалён в JDK 15; скрипты — GraalJS / внешний runtime.
- JDBC-ODBC bridge — убран ещё в 8.
- Compact Profiles — исторически; на модулях (JPMS, Java 9+) другая модель.

С точки зрения дизайна Java 8 — сдвиг к **функциям как значениям** и **конвейерам преобразований**
над данными, без отказа от ООП: лямбда — реализация SAM-интерфейса, а не отдельный тип функций.

---

## 2. Лямбда-выражения

Лямбда-выражение (lambda expression) — литерал, который реализует единственный абстрактный
метод (Single Abstract Method, SAM) целевого функционального интерфейса.

```java
@FunctionalInterface
interface Operation {
    int apply(int x, int y);
}

Operation sum = (x, y) -> x + y;
int result = sum.apply(10, 20); // 30
```

Синтаксис:

| Форма | Пример |
|-------|--------|
| Параметры | `(x, y) -> …`, `x -> …`, `() -> …` |
| Типы | можно опустить (target typing) или указать явно |
| Expression body | `(x, y) -> x + y` — значение выражения = результат |
| Block body | `(x, y) -> { …; return …; }` — нужен явный `return`, если метод не `void` |

Лямбда **не выполняется** в месте объявления: создаётся объект (или вызывается
через `invokedynamic` / лямбда-метафабрику), а тело вызывается при вызове SAM-метода.
Это и есть отложенное исполнение (deferred execution).

Target typing: тип лямбды выводится из контекста (присваивание, аргумент метода,
cast). Одна и та же запись `(a, b) -> a + b` может быть и `BinaryOperator<Integer>`,
и `IntBinaryOperator` — в зависимости от целевого типа.

Лямбда — не «синтаксический сахар над анонимным классом» один в один:

- у лямбды нет собственного `this`: `this` / `super` — объемлющий класс;
- нельзя объявить новые члены «класса»;
- семантика захвата и генерация байткода другие (`invokedynamic` + `LambdaMetafactory`).

```java
class Service {
    void run() {
        Runnable r = () -> System.out.println(this.getClass().getSimpleName());
        // this — Service, не «анонимный Runnable»
        r.run();
    }
}
```

---

## 3. Захват переменных

Из лямбды доступны:

- поля экземпляра и `static` поля (чтение/запись — как обычно);
- локальные переменные и параметры — только если они **effectively final**
  (не переназначаются после инициализации; явное `final` не обязательно).

```java
int factor = 2;
IntUnaryOperator mul = x -> x * factor; // OK
// factor = 3; // сломает компиляцию захвата
```

Зачем ограничение: захват локальной переменной копирует **значение** (для ссылки —
саму ссылку) в момент создания лямбды. Переназначение локальной переменной сделало бы
модель памяти неоднозначной относительно «когда видно новое значение».

Мутация **содержимого** объекта, на который указывает effectively final ссылка, разрешена:

```java
List<String> acc = new ArrayList<>();
Consumer<String> c = acc::add; // ссылка acc не меняется
c.accept("x");
```

Из тела лямбды **нельзя** вызывать `default`-методы того функционального интерфейса,
который эта лямбда реализует (у лямбды нет «себя» как типа интерфейса с default).
Нужен явный вызов через имя интерфейса / отдельный объект.

Демо: [LambdaCaptureDemo.java](code/LambdaCaptureDemo.java).

---

## 4. Ссылки на методы и конструкторы

Method reference — компактная форма лямбды вида «вызвать уже существующий метод».

| Вид | Синтаксис | Эквивалент |
|-----|-----------|------------|
| Static | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Bound instance | `System.out::println` | `x -> System.out.println(x)` |
| Unbound instance | `String::length` | `s -> s.length()` |
| Constructor | `ArrayList::new` | `() -> new ArrayList<>()` |
| Array constructor | `String[]::new` | `n -> new String[n]` |

`System.out::println` — bound instance reference: получатель (`PrintStream out`) зафиксирован,
SAM-метод принимает аргумент, который уходит в `println`.

Unbound: первый параметр SAM становится receiver'ом:

```java
Function<String, Integer> len = String::length;
BiPredicate<String, String> eq = String::equals; // (a, b) -> a.equals(b)
```

Когда выбирать:

- method reference — если тело = один вызов без адаптации аргументов;
- лямбда — если нужна композиция, null-check, приведение типов, несколько строк.

Утверждение «method reference всегда эффективнее лямбды» — упрощение. Часто байткод
сопоставим; выигрыш скорее в **читаемости и однозначности намерения**, а не в микрооптимизации.

Демо: [MethodReferenceDemo.java](code/MethodReferenceDemo.java).

---

## 5. Функциональные интерфейсы

Функциональный интерфейс — интерфейс ровно с **одним абстрактным методом**
(методы `Object` вроде `equals` не считаются; `default`/`static` не считаются).

`@FunctionalInterface` — опциональная проверка намерения: второй абстрактный метод —
ошибка компиляции. Без аннотации интерфейс всё равно может быть функциональным.

### 5.1. Базовые семейства (`java.util.function`)

| Интерфейс | SAM | Смысл |
|-----------|-----|--------|
| `Function<T,R>` | `R apply(T)` | преобразование |
| `UnaryOperator<T>` | `T apply(T)` | `Function<T,T>` |
| `BiFunction<T,U,R>` | `R apply(T,U)` | два входа |
| `BinaryOperator<T>` | `T apply(T,T)` | `BiFunction<T,T,T>` |
| `Predicate<T>` | `boolean test(T)` | условие |
| `BiPredicate<T,U>` | `boolean test(T,U)` | условие на пару |
| `Consumer<T>` | `void accept(T)` | side-effect |
| `BiConsumer<T,U>` | `void accept(T,U)` | side-effect на пару |
| `Supplier<T>` | `T get()` | фабрика / ленивое значение |

Композиция через default-методы:

```java
Function<String, Integer> toInt = Integer::valueOf;
Function<Integer, Integer> square = x -> x * x;
Function<String, Integer> pipeline = toInt.andThen(square);
// compose: g.compose(f) == g(f(x)); andThen: f.andThen(g) == g(f(x))

Predicate<String> nonEmpty = s -> !s.isEmpty();
Predicate<String> shortStr = s -> s.length() < 10;
Predicate<String> both = nonEmpty.and(shortStr);
```

### 5.2. Примитивные специализации

Чтобы избежать boxing в горячих циклах, есть варианты с `int` / `long` / `double`
в сигнатуре (не с `Integer`/`Long`/`Double` как типом параметра):

| Паттерн | Пример | SAM |
|---------|--------|-----|
| Примитив → объект | `IntFunction<R>`, `LongFunction<R>`, `DoubleFunction<R>` | `R apply(int)` и т.д. |
| Объект → примитив | `ToIntFunction<T>`, `ToLongFunction<T>`, `ToDoubleFunction<T>` | `int applyAsInt(T)` |
| Примитив → примитив | `IntUnaryOperator`, `LongBinaryOperator`, … | `applyAsInt` / `applyAsLong` |
| Предикат/consumer/supplier | `IntPredicate`, `LongConsumer`, `BooleanSupplier`, … | без boxing на границе |
| Смешанный consumer | `ObjIntConsumer<T>` | `void accept(T, int)` |
| Bi → примитив | `ToIntBiFunction<T,U>` | `int applyAsInt(T,U)` |
| Примитив ↔ примитив | `IntToLongFunction`, `DoubleToIntFunction`, … | узкие конверсии |

Именно поэтому у Stream есть `mapToInt` / `IntStream`: цепочка без `Integer` на каждом шаге.

Демо: [FunctionalInterfacesDemo.java](code/FunctionalInterfacesDemo.java).

---

## 6. Default и static методы в интерфейсах

### Default

`default` — реализация метода прямо в интерфейсе. Класс-наследник **может** не
переопределять метод и получит эту реализацию.

Зачем ввели: эволюция API без массовой поломки реализаций (`Collection.stream()`,
`List.sort`, `Iterator.forEachRemaining`).

Конфликты при множественном наследовании типов:

1. Класс vs интерфейс — побеждает метод **класса** (включая унаследованный от суперкласса).
2. Два интерфейса с одинаковым `default` — класс **обязан** переопределить и выбрать
   реализацию через `InterfaceName.super.method()`.
3. `default` в одном интерфейсе и `abstract` с той же сигнатурой в другом — тоже нужно
   явно реализовать в классе.
4. `default` **не может** переопределить методы `Object` (`equals`, `hashCode`, `toString`).

```java
interface A {
    default String tag() { return "A"; }
}
interface B {
    default String tag() { return "B"; }
}
class C implements A, B {
    @Override
    public String tag() {
        return A.super.tag() + "+" + B.super.tag();
    }
}
```

### Static

`static` в интерфейсе вызывается только как `InterfaceName.method(...)`.
Не наследуется «через экземпляр», не участвует в override. Удобно для фабрик и утилит
рядом с типом (`Comparator.comparing`, `Predicate.isEqual` — паттерн того же рода,
хотя часть — в классах-утилитах).

С Java 9+ появились ещё `private` методы в интерфейсах (для переиспользования между
default/static) — полезно знать как эволюцию идеи Java 8.

Демо: [DefaultMethodConflictDemo.java](code/DefaultMethodConflictDemo.java).

---

## 7. Optional

`Optional<T>` — контейнер «значение есть / значения нет». Цель API — **явный возврат
отсутствующего результата** из метода, а не универсальная замена всех `null` в модели.

```java
Optional<String> o = Optional.of("hello");
o.isPresent();
o.ifPresent(s -> System.out.println(s.length()));
o.orElse("fallback");
o.orElseGet(() -> loadDefault()); // lazy
o.orElseThrow();                   // Java 10+; раньше orElseThrow(supplier)
```

Правила использования на практике:

| Делать | Не делать |
|--------|-----------|
| Возвращать из метода, где отсутствие — нормальный исход | Поля entity / DTO / параметры |
| `Optional.ofNullable(x).map(...).orElse(...)` в потоке вычислений | `optional.get()` без проверки / без `orElseThrow` |
| `Optional.empty()` / `of` / `ofNullable` по смыслу | `Optional.of(null)` — NPE |
| Примитивные `OptionalInt` / `OptionalLong` / `OptionalDouble` в числовых API | Оборачивать коллекции: пустая коллекция уже говорит «нет элементов» |

`Optional` не потокобезопасен как mutable holder и не предназначен для сериализации как
часть публичного wire-формата.

Демо: [OptionalPitfallsDemo.java](code/OptionalPitfallsDemo.java).

---

## 8. Stream API

`java.util.stream.Stream<T>` — **не коллекция**, а абстракция конвейера (pipeline):
источник + промежуточные операции (intermediate) + терминальная (terminal).

Свойства:

- **ленивость** — промежуточные операции не бегут, пока нет terminal;
- **одноразовость** — после terminal стрим «съеден»; повторный terminal — `IllegalStateException`;
- **возможный параллелизм** — через `parallel()` / `parallelStream()`;
- **encounter order** — зависит от источника и операций (`unordered()` снимает требование порядка).

`Map` сам по себе не стримится: `map.keySet().stream()`, `map.values().stream()`,
`map.entrySet().stream()`.

### 8.1. Создание

```java
list.stream();
Stream.of("a", "b");
Arrays.stream(array);
Files.lines(path);                 // AutoCloseable — prefer try-with-resources
"text".chars();                    // IntStream code points/chars
Stream.builder().add("x").build();
Stream.iterate(0, n -> n + 1);     // бесконечный; с Java 9 — iterate(seed, hasNext, next)
Stream.generate(() -> UUID.randomUUID());
IntStream.range(0, 10);
Random.ints()…                     // из java.util.Random / ThreadLocalRandom
```

### 8.2. Intermediate (часто используемые)

| Метод | Роль |
|-------|------|
| `filter` | отбор по `Predicate` |
| `map` / `mapToInt`… | 1→1 преобразование; примитивные — без boxing |
| `flatMap` / `flatMapToInt`… | 1→0..N, «разворачивание» вложенных структур |
| `distinct` | уникальность через `equals` |
| `sorted` | сортировка; stateful, буферизует |
| `peek` | side-effect для отладки; в проде легко злоупотребить |
| `limit` / `skip` | short-circuiting / отсечение |
| `takeWhile` / `dropWhile` | Java 9+ |
| `unordered` | снять требование порядка (важно для parallel) |

`flatMap` — типичный паттерн «список списков → плоский поток», парсинг, optional→stream
(`o.stream()` с Java 9, раньше `o.map(Stream::of).orElseGet(Stream::empty)`).

### 8.3. Terminal

| Метод | Роль |
|-------|------|
| `forEach` / `forEachOrdered` | потребление; parallel: порядок у `forEach` не гарантирован |
| `collect` | свертка в структуру через `Collector` |
| `reduce` | свёртка к одному значению (см. [§8.4](#84-reduce)) |
| `count` / `min` / `max` | агрегаты |
| `anyMatch` / `allMatch` / `noneMatch` | short-circuit предикаты |
| `findFirst` / `findAny` | поиск; в parallel `findAny` обычно дешевле |
| `toArray` | массив |
| у `IntStream`: `sum`, `average`, `summaryStatistics` | числовые агрегаты |

### 8.4. `reduce`

Свёртка потока в одно значение: на каждом шаге берётся накопленное и следующий элемент.

Аналогия: стопка значений; правило «как смешать два» применяется, пока не останется одно.

#### Три перегрузки

**1. С нейтральным элементом (identity)** — тип результата = тип элемента:

```java
int sum = stream.reduce(0, (acc, x) -> acc + x);
// или: stream.reduce(0, Integer::sum);
```

- identity — старт и ответ для пустого стрима (`0` для суммы, `1` для произведения…);
- accumulator `(acc, x) → новый acc` — как смешать накопление и элемент.

Пустой стрим → вернёт identity.

**2. Без identity → `Optional`:**

```java
Optional<Integer> sum = stream.reduce(Integer::sum);
```

Пустой стрим → `Optional.empty()`. Непустой: первый элемент — старт, дальше accumulator.

**3. Три аргумента** — результат другого типа и/или parallel:

```java
U result = stream.reduce(
        identity,     // стартовое U
        accumulator,  // (U, T) → U  — элемент в накопление
        combiner      // (U, U) → U  — склеить два накопления (parallel)
);
```

В последовательном стриме combiner почти не участвует; в параллельном без него нельзя
корректно склеить частичные результаты разных потоков.

#### Пошагово

`[1, 2, 3, 4]`, `reduce(0, Integer::sum)`:

```
acc = 0
acc = 0+1 → 1
acc = 1+2 → 3
acc = 3+3 → 6
acc = 6+4 → 10
```

В parallel стрим режут на куски, каждый сворачивают, затем combiner склеивает куски.
Операция должна быть **ассоциативной**: `(a ⊕ b) ⊕ c = a ⊕ (b ⊕ c)`. Сумма / max — ок;
вычитание и «хрупкая» конкатенация — нет (sequential и parallel могут разойтись).

#### `reduce` vs `collect`

| | `reduce` | `collect` |
|--|----------|-----------|
| Цель | одно значение (число, `Optional`, редко буфер) | структура: `List`, `Map`, grouping… |
| Стиль | обычно новый результат на шаг | supplier + мутабельный накопитель + combiner |
| Практика | простые агрегаты; часто лучше `IntStream.sum()` / `max()` | `Collectors.*` |

Многие `collect` — та же идея свёртки, заточенная под контейнеры.

Демо: [ReduceDemo.java](code/ReduceDemo.java).

### 8.5. `collect` и `Collectors`

`collect` принимает `Collector` с supplier / accumulator / combiner / finisher.
Готовые фабрики:

- `toList()` (до Java 16 — mutable `ArrayList`; с 16 есть ещё `Stream.toList()` — unmodifiable);
- `toSet()`, `toCollection(supplier)`;
- `toMap` / `toConcurrentMap` (обязательно думать про collision policy);
- `groupingBy` / `partitioningBy`;
- `joining`, `mapping`, `collectingAndThen`;
- `summarizing*`, `averaging*`, `summing*`.

```java
Collector<String, ?, List<String>> toList = Collector.of(
        ArrayList::new,
        List::add,
        (a, b) -> { a.addAll(b); return a; }
);
```

### 8.6. Stream vs Collection

| | Collection | Stream |
|--|------------|--------|
| Суть | структура данных, хранение | конвейер вычислений |
| Доступ | по индексу/ключу/итератору | только через операции пайплайна |
| Повтор | можно обходить многократно | один terminal |
| Мутация | часто изменяема | источник лучше не мутировать во время прохода |
| Цель | владеть элементами | описать *что* сделать с элементами |

Демо: [StreamPipelineDemo.java](code/StreamPipelineDemo.java).

---

## 9. Параллельные стримы

`parallelStream()` / `stream().parallel()` включают выполнение на общем
`ForkJoinPool.commonPool()` (размер по умолчанию ≈ `cores - 1`).

Важные факты контракта:

- Режим sequential/parallel — **свойство всего пайплайна** на момент terminal.
  Цепочка `.parallel().map(...).sequential().reduce(...)` в итоге **последовательная**:
  побеждает последний вызов перед terminal. Поэтапного «peek sequential, map parallel»
  в одном пайплайне нет.
- Порядок: источники вроде `List` — ordered; `forEach` в parallel порядок не обещает;
  `forEachOrdered` / часть collect сохраняют encounter order ценой накладных расходов.
- `findAny` vs `findFirst`, `unordered()` — рычаги, когда порядок не нужен.

Когда parallel оправдан:

- достаточно большой объём CPU-bound работы на элемент;
- дешёвое разбиение источника (`ArrayList`, массив, `IntStream.range`) — не `LinkedList`;
- ассоциативный `reduce`/`collect` с нормальным combiner;
- нет блокирующего I/O внутри лямбд (иначе можно застолбить commonPool для всей JVM).

Анти-паттерн: `parallelStream()` «на всякий случай» на маленьких списках и на сетевых
вызовах в `map`. Для изоляции нагрузки — собственный `ForkJoinPool` и
`pool.submit(() -> stream.parallel()...).get()` (с осторожностью к контексту classloader/TCCL).

Демо: [ParallelStreamOrderingDemo.java](code/ParallelStreamOrderingDemo.java).

---

## 10. Новые методы Map

Java 8 добавила default-методы, заточенные под atomic-на-уровне-одной-записи сценарии
и под лямбды (в `ConcurrentHashMap` часть из них имеет более сильные concurrency-гарантии):

| Метод | Поведение |
|-------|-----------|
| `getOrDefault` | значение или default без `containsKey`+`get` |
| `putIfAbsent` | положить, только если ключа нет (или value был `null` — нюанс у обычных Map) |
| `forEach` | обход `(k,v)` |
| `replace` / `replaceAll` | условная замена / трансформация всех values |
| `compute` | всегда пересчитать value по `(k, oldV)` (oldV может быть `null`) |
| `computeIfAbsent` | вычислить value, только если ключа нет / value `null` |
| `computeIfPresent` | пересчитать, только если value есть |
| `merge` | если ключа нет — записать value; иначе — `remappingFunction(old, value)` |

Типичный prod-паттерн — кэш/индекс:

```java
map.computeIfAbsent(key, k -> load(k));
map.merge(word, 1, Integer::sum);
```

Ловушки:

- `compute*` / `merge`: возврат `null` из функции **удаляет** mapping;
- в `HashMap` эти методы не делают map потокобезопасным — нужна `ConcurrentHashMap`
  или внешняя синхронизация;
- `computeIfAbsent` не должен рекурсивно модифицировать тот же map непредсказуемо
  (в CHM — отдельные ограничения/риски deadlock-подобных сценариев при вложенных вызовах).

Демо: [MapApiJava8Demo.java](code/MapApiJava8Demo.java).

---

## 11. Date/Time API (`java.time`)

Пакет `java.time` (JSR-310): immutable, thread-safe типы вместо `java.util.Date` /
`Calendar`.

| Тип | Смысл |
|-----|--------|
| `LocalDate` | дата без времени и зоны |
| `LocalTime` | время без даты и зоны |
| `LocalDateTime` | дата+время **без** зоны |
| `ZonedDateTime` | дата+время + `ZoneId` (учитывает DST) |
| `OffsetDateTime` / `OffsetTime` | фиксированный offset, без правил зоны |
| `Instant` | точка на UTC timeline (машинное время) |
| `Duration` / `Period` | интервалы: машинный / календарный |
| `DateTimeFormatter` | парсинг/форматирование вместо `SimpleDateFormat` |

```java
LocalDate.now();
LocalDate.now().plusWeeks(1).plusMonths(1).plusYears(1);
LocalDate.now().plus(1, ChronoUnit.DECADES);
LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));

LocalDate secondSaturday = LocalDate.now()
        .with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY))
        .with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
// или: dayOfWeekInMonth(2, DayOfWeek.SATURDAY)

Instant now = Instant.now(); // предпочтительнее new Date().toInstant()
LocalDateTime local = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
ZonedDateTime zoned = ZonedDateTime.now(ZoneId.of("Europe/Moscow"));
```

Правило моделирования:

- хранить события «в мире» как `Instant` (или UTC) + показывать через `ZoneId`;
- `LocalDateTime` **не** означает «локальное время пользователя на сервере» само по себе —
  это «стена часов без зоны»; арифметика через DST с ним опасна;
- для «следующий вторник / вторая суббота» — `TemporalAdjusters`.

Демо: [DateTimeApiDemo.java](code/DateTimeApiDemo.java).

---

## 12. Повторяемые аннотации и прочее

### `@Repeatable`

Нужны две аннотации: повторяемая и контейнер.

```java
@Repeatable(Schedulers.class)
@interface Scheduler {
    String cron();
}

@interface Schedulers {
    Scheduler[] value();
}
```

С Java 8 контейнер можно не писать вручную на месте использования: `@Scheduler @Scheduler`.
Reflection: `getAnnotationsByType(Scheduler.class)`.

### `StringJoiner`

Собирает строки с delimiter / prefix / suffix. Обычно достаточно
`String.join` или `Collectors.joining(",", "[", "]")`; `StringJoiner` — когда нужна
пошаговая сборка в цикле.

### `Base64`

`java.util.Base64` — потокобезопасные encoder/decoder (RFC 4648 / MIME RFC 2045):

```java
String b64 = Base64.getEncoder().encodeToString("input".getBytes(StandardCharsets.UTF_8));
byte[] raw = Base64.getDecoder().decode(b64);
// также: getUrlEncoder()/getUrlDecoder(), getMimeEncoder()/getMimeDecoder()
```

### Type annotations и parameter reflection

- аннотации на использованиях типов (`List<@NonNull String>`) — инфраструктура для
  checker frameworks;
- `Executable.getParameters()` + флаг `-parameters` / компиляция с именами — удобство
  фреймворков (Spring и др.).

---

## 13. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [LambdaCaptureDemo.java](code/LambdaCaptureDemo.java) | effectively final, мутация содержимого, `this` |
| [MethodReferenceDemo.java](code/MethodReferenceDemo.java) | static / bound / unbound / constructor refs |
| [FunctionalInterfacesDemo.java](code/FunctionalInterfacesDemo.java) | compose/andThen, primitive specializations |
| [DefaultMethodConflictDemo.java](code/DefaultMethodConflictDemo.java) | конфликт default и `A.super.m()` |
| [OptionalPitfallsDemo.java](code/OptionalPitfallsDemo.java) | of/ofNullable, orElse vs orElseGet, анти-`get` |
| [StreamPipelineDemo.java](code/StreamPipelineDemo.java) | лень, flatMap, collect/groupingBy, reuse |
| [ReduceDemo.java](code/ReduceDemo.java) | три формы reduce, identity/Optional, associative vs parallel |
| [ParallelStreamOrderingDemo.java](code/ParallelStreamOrderingDemo.java) | forEach vs forEachOrdered, last parallel/sequential wins |
| [MapApiJava8Demo.java](code/MapApiJava8Demo.java) | computeIfAbsent, merge, compute → remove |
| [DateTimeApiDemo.java](code/DateTimeApiDemo.java) | Instant/Local/Zoned, adjusters |

---

## 14. Вопросы для собеседования и ответы

### 1. Чем лямбда отличается от анонимного класса, реализующего тот же интерфейс?

У лямбды нет собственного `this` (это enclosing instance), нельзя объявлять поля/новые
методы «класса», другой байткод (`invokedynamic` + metafactory). Захват локальных —
effectively final, как у анонимных классов. Семантика «объект с одним методом» похожа,
механика и модель идентичности — нет: лямбды не обязаны создавать уникальный экземпляр
на каждый site (стратегия зависит от захвата и JVM).

### 2. Почему локальная переменная должна быть effectively final?

Захват копирует значение (для ссылки — ссылку) в момент создания лямбды. Если бы
локальную переменную можно было переназначить, возникла бы гонка смыслов между стековым
кадром и захваченным значением. Мутация объекта за effectively final ссылкой остаётся
возможной — ограничение именно на **переприсваивание переменной**.

### 3. Что такое функциональный интерфейс и зачем `@FunctionalInterface`?

Интерфейс с одним абстрактным методом (SAM). Default/static не мешают; методы `Object`
не считаются. Аннотация документирует намерение и заставляет компилятор отвергнуть
второй абстрактный метод. Без аннотации интерфейс всё равно может быть целевым типом
лямбды.

### 4. Зачем примитивные `IntPredicate`, `ToLongFunction` и `IntStream`?

Чтобы убрать boxing/unboxing на горячем пути. `Stream<Integer>.map(i -> i + 1)` создаёт
кучу `Integer`; `IntStream` держит `int`. Специализированные функциональные интерфейсы —
контракт лямбд для этих стримов и для API вроде `OptionalInt`.

### 5. Как разрешаются конфликты `default`-методов?

Правило классов важнее интерфейсов. Если класс наследует два интерфейса с одинаковым
default (или default vs abstract с той же сигнатурой), он обязан переопределить метод.
Вызов конкретной реализации — `InterfaceName.super.method()`. Default не может
переопределить `Object`-методы.

### 6. Почему `Optional` не стоит класть в поля и параметры?

Он создан как return-type pattern для «значение может отсутствовать». В полях/параметрах
появляется двойная nullable-модель (`null` vs empty), раздувается API, усложняется
сериализация и JPA-маппинг. Для коллекций отсутствие элементов уже выражено пустой
коллекцией. Внутри domain чаще явная модель (`null`, `Nullable`, отдельный тип отсутствия).

### 7. Почему промежуточные операции Stream ленивы и зачем это важно?

Pipeline строится как описание; работа начинается на terminal. Это позволяет short-circuit
(`findFirst`, `anyMatch`, `limit`) не тянуть весь источник и сливать проходы. Цена —
сложнее рассуждать о side-effects в `map`/`peek`: они могут не вызваться или вызваться
не тогда, когда ожидают. Side-effects в intermediate — запах; бизнес-эффекты лучше в
terminal / явных циклах.

### 8. Чем `map` отличается от `flatMap`?

`map`: один элемент → один элемент (тип результата может смениться).
`flatMap`: один элемент → поток нуля или многих; вложенность «сплющивается».
Классика: `Stream<List<T>>` → `Stream<T>`, разбиение строк, разворот `Optional` в 0..1
элемент. Ошибка новичка — `map` туда, где нужен `flatMap`, и получение
`Stream<Stream<T>>`.

### 9. Можно ли смешать parallel и sequential стадии в одном пайплайне?

Нет в смысле «часть операций на commonPool, часть строго последовательно внутри одного
terminal». `parallel()` / `sequential()` переключают режим всего стрима; действует
состояние на момент terminal. Для разных режимов нужны разные пайплайны или явное
разделение этапов (собрали список → второй проход).

### 10. Когда `parallelStream` ускорит, а когда замедлит?

Ускорит на большом CPU-bound объёме с хорошим spliterator (массив/`ArrayList`/`range`)
и ассоциативной агрегацией. Замедлит на малых данных (overhead форков), на
`LinkedList`, при сильной зависимости от порядка, при блокирующем I/O в лямбдах
(конкуренция за commonPool). Всегда измерять; не включать «потому что Java 8».

### 11. Чем `computeIfAbsent` отличается от `get` + `put`?

Атомарность и выразительность на уровне одной записи: в `ConcurrentHashMap`
`computeIfAbsent` гарантирует, что mapping создаётся один раз при гонке (с оговорками
по рекурсивным обновлениям). В обычном `HashMap` гонок всё равно не избежать без
внешней синхронизации, но исчезает TOCTOU-стиль кода и повторное вычисление фабрики
в однопоточном сценарии. Фабрика не должна неожиданно портить тот же map.

### 12. Почему `LocalDateTime` нельзя слепо использовать для «времени события»?

У него нет зоны. Одинаковая «стена часов» в Москве и в UTC — разные `Instant`.
Для факта «когда произошло» — `Instant` (или `ZonedDateTime`/`OffsetDateTime` с явной зоной).
`LocalDateTime` уместен для расписаний «13:00 по стене», локальных отчётов, UI без
претензии на абсолютный момент. DST-переходы с «локальной» арифметикой без зоны —
источник багов.

### 13. Что случилось с Nashorn и почему вопрос ещё встречается?

В Java 8 Nashorn — встроенный JS-движок + CLI `jjs`. С JDK 11 deprecated, с 15 удалён.
На собесе достаточно: «часть Java 8, сейчас не использовать; альтернативы — GraalJS /
внешний движок». Важно отличать исторический факт платформы от текущего стека.

### 14. Чем `orElse` отличается от `orElseGet`?

`orElse(value)` всегда вычисляет аргумент (дорогое создание default — зря).
`orElseGet(supplier)` вызывает supplier только при пустом `Optional`. Для констант —
без разницы; для `loadFromDb()` — только `orElseGet` / `orElseThrow`.

### 15. Чем отличаются три перегрузки `Stream.reduce`?

1. `reduce(identity, accumulator)` — всегда возвращает значение; пустой стрим → identity.
2. `reduce(accumulator)` — `Optional`; пустой стрим → empty; старт — первый элемент.
3. `reduce(identity, accumulator, combiner)` — тип результата может отличаться от элемента;
   combiner склеивает частичные `U` в parallel.

Демо: [ReduceDemo.java](code/ReduceDemo.java).

### 16. Зачем combiner в `reduce`, если в sequential его «не видно»?

В одном потоке достаточно accumulator: элементы идут слева направо. В parallel стрим
делят на сегменты; каждый сегмент даёт своё накопление типа `U`, и combiner обязан
ассоциативно склеить эти `U`. Без корректного combiner (или при неассоциативном
операторе вроде вычитания) parallel даёт неверный или нестабильный относительно
sequential результат. Для суммы часто пишут одну `Integer::sum` и в accumulator, и в
combiner.

### 17. Когда выбрать `reduce`, а когда `collect` / `IntStream.sum`?

Простой числовой агрегат — `mapToInt(...).sum()` / `max()` / `summaryStatistics()`:
читаемее и без boxing. `reduce` — когда нужна своя бинарная операция или свёртка к
нестандартному скаляру. Сборка `List`/`Map`/grouping — `collect` (мутабельный контейнер +
готовые `Collectors`). `collect` тоже свёртка, но контракт заточен под структуры, а не
под «одно значение».
