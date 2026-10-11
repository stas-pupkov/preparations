# Java Collections (коллекции Java)

Java 17+. Заметка для изучения и подготовки к собеседованиям.

## Содержание

1. [Что такое коллекция](#1-что-такое-коллекция)
2. [Иерархия интерфейсов](#2-иерархия-интерфейсов)
3. [Collection и Collections](#3-collection-и-collections)
4. [Iterable, Iterator, ListIterator](#4-iterable-iterator-listiterator)
5. [Fail-fast и ConcurrentModificationException](#5-fail-fast-и-concurrentmodificationexception)
6. [List: ArrayList, LinkedList, Vector](#6-list-arraylist-linkedlist-vector)
7. [Queue и Deque](#7-queue-и-deque)
8. [Set](#8-set)
9. [Map и HashMap](#9-map-и-hashmap)
10. [Особые Map: LinkedHashMap, TreeMap, WeakHashMap, IdentityHashMap](#10-особые-map-linkedhashmap-treemap-weakhashmap-identityhashmap)
11. [Синхронизация и только для чтения](#11-синхронизация-и-только-для-чтения)
12. [Сложность и выбор коллекций](#12-сложность-и-выбор-коллекций)
13. [Практика и полезные приёмы](#13-практика-и-полезные-приёмы)
14. [Null в коллекциях](#14-null-в-коллекциях)
15. [Демо-код](#15-демо-код)
16. [Вопросы для собеседования и ответы](#16-вопросы-для-собеседования-и-ответы)

---

## 1. Что такое коллекция

Коллекция — структура данных, набор объектов. Элементами могут быть числа, строки,
объекты пользовательских классов и т.п.

В Java Collections Framework (JCF) наверху два разных мира:

- `Collection` — набор элементов;
- `Map` — набор пар «ключ — значение».

`Map` не является `Collection`: у него другой контракт (ключ → значение), а не «мешок
элементов».

---

## 2. Иерархия интерфейсов

```text
Iterable
 └── Collection
      ├── List
      ├── Set
      │    └── SortedSet
      │         └── NavigableSet
      └── Queue
           └── Deque

Map
 └── SortedMap
      └── NavigableMap

Iterator   (отдельная ветка; ListIterator extends Iterator)
```

Кратко по смыслу:

| Интерфейс | Смысл | Типичные реализации |
|-----------|-------|---------------------|
| `List` | упорядоченный список, допускает дубликаты, доступ по индексу | `ArrayList`, `LinkedList` |
| `Set` | множество без дубликатов | `HashSet`, `LinkedHashSet`, `TreeSet`, `EnumSet` |
| `Queue` | очередь; часто FIFO | `PriorityQueue`, реализации `Deque` |
| `Deque` | двусторонняя очередь; FIFO или LIFO | `ArrayDeque`, `LinkedList` |
| `Map` | ключ → значение | `HashMap`, `LinkedHashMap`, `TreeMap`, `WeakHashMap`, `EnumMap` |

Устаревшие, но всё ещё встречаются: `Vector`, `Stack`, `Hashtable`. В новом коде их
обычно не берут.

`Deque` расширяет `Queue` (не наоборот).

---

## 3. Collection и Collections

- `java.util.Collection` — интерфейс «набор элементов»;
- `java.util.Collections` — утилитарный класс со статическими методами:
  `sort`, `unmodifiableList`, `synchronizedMap`, `emptyList`, `frequency` и т.д.

Имена похожи — смысл разный.

---

## 4. Iterable, Iterator, ListIterator

### Связь

`Iterable` имеет метод `iterator()`, который возвращает `Iterator`.

Классы с `Iterable` можно писать в for-each:

```java
for (String s : list) {
    // ...
}
```

Под капотом for-each берёт `Iterator`.

### Iterator

Основные методы: `hasNext()`, `next()`, `remove()`.

Важные нюансы:

- `hasNext()` только проверяет наличие следующего элемента, сам по коллекции не двигается;
- `next()` без `hasNext()` допустим, но на конце коллекции даст `NoSuchElementException`;
- десять вызовов `hasNext()` подряд ничего не «пропускают»;
- `remove()` удаляет элемент, который вернул последний `next()`. Без предшествующего
  `next()` — `IllegalStateException`.

### ListIterator

Расширяет `Iterator`, только для `List`.

Дополнительно:

- обход в обе стороны (`previous()`);
- `add()`, `set()`;
- позиция «между» элементами, а не «на» одном элементе.

### Enumeration

Старый обходчик из `Vector` / `Hashtable`. Не умеет удалять, имена методов хуже читаются.
В новом коде — `Iterator`.

---

## 5. Fail-fast и ConcurrentModificationException

### Fail-fast

Fail-fast поведение (быстрый отказ) — при опасном состоянии сразу падать, а не продолжать
с непредсказуемым результатом.

Итераторы многих коллекций JCF (`ArrayList`, `HashMap`, …) fail-fast: если после создания
итератора коллекцию изменили «снаружи» (не через `iterator.remove()`), при следующем
обращении к итератору будет `ConcurrentModificationException`.

Как обычно устроено:

1. у коллекции есть счётчик модификаций (`modCount`);
2. итератор запоминает ожидаемое значение;
3. при каждом шаге сравнивает; не совпало — исключение.

Это ловит и однопоточные ошибки:

```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3));
for (Integer x : list) {
    list.remove(1); // CME
}
```

Демо: [FailFastDemo.java](code/FailFastDemo.java).

### Как безопасно менять при обходе

- удалять через `iterator.remove()`;
- для `List` — `list.subList(...).clear()` или собрать индексы и удалить отдельно;
- в многопотоке — `CopyOnWriteArrayList`, `ConcurrentHashMap` и т.п.;
- обертки `Collections.synchronizedList` сами по себе CME при обходе без внешней
  синхронизации не отменяют.

### Fail-safe — осторожная формулировка

В учебниках часто пишут: fail-safe итератор работает с клоном и не бросает CME.

На практике:

- `CopyOnWriteArrayList` — итератор по **снимку** на момент создания;
- `ConcurrentHashMap` — **weakly consistent** (слабо согласованный) обход: CME не бросает,
  но не обещает увидеть все изменения и не обязан клонировать всю map.

Поэтому точнее говорить «не fail-fast / weakly consistent / snapshot», а не сводить всё к
слову fail-safe.

---

## 6. List: ArrayList, LinkedList, Vector

### ArrayList

`ArrayList` реализует `List` поверх обычного массива. В отличие от массива Java, длина
которого фиксирована после создания, `ArrayList` умеет расти во время выполнения.
Размер при создании указывать не обязательно. Элементы — любые ссылочные типы, в том
числе `null`.

Внутри два ключевых поля:

- `elementData` — массив-хранилище (capacity = `elementData.length`);
- `size` — реальное число элементов.

`size` и `capacity` — разные вещи. В массиве почти всегда есть «запас» пустых слотов.

Наглядный разбор с картинками (старый, но полезный визуально):
[Структуры данных в картинках. ArrayList](https://habr.com/ru/articles/128269/) (Habr, 2011).
Ниже — та же механика с поправками под Java 17+ / OpenJDK 21.

#### Создание

```java
ArrayList<String> list = new ArrayList<>();
ArrayList<String> withCap = new ArrayList<>(100); // своя начальная capacity
```

В старых JDK (как в статье 2011) конструктор без параметров сразу выделял массив на 10
элементов: `elementData = (E[]) new Object[10]`.

В современных JDK по умолчанию список **пустой и «ленивый»**:

- `new ArrayList<>()` кладёт в `elementData` пустой shared-массив
  (`DEFAULTCAPACITY_EMPTY_ELEMENTDATA`);
- при **первом** `add` capacity становится `DEFAULT_CAPACITY = 10`
  (или больше, если просили через `ensureCapacity` / большой `addAll`).

То есть «десятка по умолчанию» осталась, но выделяется не в конструкторе, а при первом
заполнении. Если размер заранее известен — лучше `new ArrayList<>(expectedSize)`, чтобы
реже делать grow.

#### Добавление в конец

```java
list.add("0");
```

Упрощённо:

1. убедиться, что в массиве хватает места (`grow`, если нужно);
2. записать элемент в `elementData[size]` и увеличить `size`.

```java
elementData[size++] = element;
```

Пока есть свободные слоты, `add` в конец — очень дёшево (амортизированно O(1)).

#### Рост массива (grow / ensureCapacity)

Если места нет, создаётся больший массив, старые элементы копируются
(`Arrays.copyOf` / внутри — native `System.arraycopy`).

Формула роста:

| Эпоха | Как считают новую capacity |
|-------|----------------------------|
| Старые JDK (как в статье) | `(oldCapacity * 3) / 2 + 1` |
| Современный OpenJDK | предпочтительно `old + (old >> 1)` ≈ **×1.5**, но не меньше запрошенного минимума; через `ArraysSupport.newLength` с защитой от overflow |

Итог тот же по смыслу: растём примерно в полтора раза, копируем все живые элементы — это
O(n) на конкретный grow, поэтому средний `add` в конец остаётся амортизированным O(1).

Пример сценария из статьи: добавляем элементы, пока не кончится capacity; на
«переполнении» — новый массив + copy, дальше снова дешёвые `add`.

Публичный `ensureCapacity(minCapacity)` позволяет заранее попросить запас (если знаете, что
сейчас нальёте много элементов).

#### Добавление в «середину»

```java
list.add(5, "100");
```

Три шага:

1. проверить/расширить capacity;
2. сдвинуть хвост вправо через `System.arraycopy`:

```java
System.arraycopy(elementData, index, elementData, index + 1, size - index);
```

3. записать новый элемент в `elementData[index]` и сделать `size++`.

Сложность — O(n) из‑за сдвига. Если при вставке ещё и не хватило capacity, копирование
будет **дважды**: один раз в grow, второй — сдвиг хвоста. Это заметно на больших списках.

Если нужно вставить **целую коллекцию** в середину — берите `addAll(index, collection)`:
внутри несколько `arraycopy`, но это всё равно быстрее поэлементного `add` в цикле.

#### Удаление

Два способа:

- по индексу — `remove(index)`;
- по значению — `remove(value)` (линейный поиск, удаляется **первый** найденный).

Удаление по индексу:

1. посчитать, сколько элементов сдвинуть: `numMoved = size - index - 1`;
2. сдвинуть хвост влево (`System.arraycopy`);
3. уменьшить `size` и обнулить освободившийся слот:

```java
elementData[--size] = null; // чтобы GC мог собрать объект
```

Важно: **capacity при `remove` не уменьшается**. Список из миллиона элементов после
`clear()`/`remove` может продолжать держать огромный массив. Это классическая «логическая»
утечка памяти. Лечение — `trimToSize()` (или новый список нужного размера).

Удаление диапазона из середины — одним куском, не `remove` в цикле:

```java
list.subList(from, to).clear(); // эффективный arraycopy
```

Демо: [ArrayListBulkRemoveDemo.java](code/ArrayListBulkRemoveDemo.java).

#### Сложность и свойства (итог)

| Операция | Оценка |
|----------|--------|
| `get` / `set` по индексу | O(1) |
| `add` / `remove` в конец (без grow) | амортизированно O(1) |
| поиск / `contains` / `remove(value)` | O(n) |
| вставка / удаление из середины | O(n) из‑за сдвига |
| grow при нехватке места | O(n) копирование |

Прочее:

- можно хранить `null`;
- **не** синхронизирован (в отличие от `Vector`);
- fail-fast итератор (см. [раздел 5](#5-fail-fast-и-concurrentmodificationexception));
- мало накладных расходов на элемент по сравнению с `LinkedList` (нет объекта `Node` на
  каждый элемент).

### LinkedList

`LinkedList` — двусвязный список (doubly linked list): каждый элемент знает предыдущий и
следующий. Реализует и `List`, и `Deque`. Итератор умеет ходить в обе стороны. Можно
хранить любые ссылки, в том числе `null`.

Наглядный разбор с картинками:
[Структуры данных в картинках. LinkedList](https://habr.com/ru/articles/127864/) (Habr, 2011).
Ниже — та же механика с поправками под современный OpenJDK.

#### Создание и устройство

```java
List<String> list = new LinkedList<>();
```

В статье 2011 внутри был sentinel-узел `header` (фиктивный элемент, `element == null`, а
`next`/`prev` замыкались на него в пустом списке) и вложенный класс `Entry`.

В современных JDK sentinel нет. У списка два указателя:

- `first` — первый узел (или `null`, если список пуст);
- `last` — последний узел;
- `size` — число элементов.

Узел — static nested class `Node`:

```java
private static class Node<E> {
    E item;
    Node<E> next;
    Node<E> prev;
}
```

На каждый элемент — отдельный объект `Node` (item + две ссылки). Памяти и давления на
аллокатор заметно больше, чем у `ArrayList`.

#### Добавление в начало / конец

`add(value)` / `addLast`, а также `addFirst` выполняются за O(1): создаётся `Node`,
перелинковываются соседи, обновляются `first`/`last` и `size`.

Упрощённо для конца (`linkLast`):

1. создать узел с `prev = last`, `next = null`;
2. сделать его новым `last`;
3. если список был пуст — он же становится `first`, иначе у старого `last.next = newNode`.

Аналогично `linkFirst` для головы. Grow массива, как у `ArrayList`, здесь нет.

#### Добавление в «середину» по индексу

```java
list.add(1, "100");
```

Сама вставка рядом с уже найденным узлом — O(1) (переставить несколько ссылок). Дорого
другое: **найти** узел с нужным индексом.

Метод `node(index)` (в старой статье — `entry(index)`) идёт с головы или с хвоста в
зависимости от `index < (size >> 1)`. То есть обходится не больше половины списка, но
асимптотика всё равно O(n). Неверный индекс → `IndexOutOfBoundsException`.

Поэтому `list.add(list.size() / 2, x)` для `LinkedList` — это O(n) на поиск + O(1) на
линковку. Часто медленнее ожиданий «связный список же для вставок».

#### Удаление

- из начала / конца — `removeFirst` / `removeLast` за O(1);
- по индексу / по значению — сначала найти узел (O(n)), потом `unlink` за O(1).

`remove(value)` идёт по цепочке и удаляет **первый** найденный.

`unlink` перекидывает `prev.next` / `next.prev`, обнуляет ссылки у узла (`item` / `next` /
`prev = null`), уменьшает `size` — чтобы GC мог собрать и узел, и элемент, если на него
больше никто не ссылается.

#### Итераторы

- `listIterator()` — с начала; `listIterator(index)` — с позиции;
- `descendingIterator()` — обход с конца без медленного `get(i)`.

`ListIterator` fail-fast: изменение списка мимо методов итератора →
`ConcurrentModificationException`.

Важный практический момент из итогов статьи: вставка/удаление **через**
`ListIterator.add` / `ListIterator.remove`, когда итератор уже стоит у нужного места, —
O(1). Дорого именно «дойти» до середины по индексу.

#### Сложность и свойства (итог)

| Операция | Оценка |
|----------|--------|
| `addFirst` / `addLast` / `removeFirst` / `removeLast` | O(1) |
| `get(i)` / `add(i, x)` / `remove(i)` | O(n) из‑за поиска узла |
| `contains` / `remove(value)` | O(n) |
| `ListIterator.add` / `remove` у текущей позиции | O(1) |

Прочее:

- можно хранить `null`;
- **не** синхронизирован;
- удобен как стек / очередь / deque с доступом к концам O(1);
- на практике для стека/очереди чаще берут `ArrayDeque` (нет объектов `Node`, лучше
  locality);
- как обычный `List` с частым random access — почти всегда хуже `ArrayList`.

### Vector и Stack

`Vector` — старый синхронизированный динамический массив. Рост historically часто ×2,
методы synchronized → лишняя цена в однопотоке.

`Stack` extends `Vector` и считается устаревшим: нарушает идею стека (доступ по индексу),
частично синхронизирован. Замена — `Deque`, обычно `ArrayDeque`.

### FIFO и LIFO

- FIFO (First-In-First-Out) — типичная `Queue` / `Deque` как очередь;
- LIFO (Last-In-First-Out) / FILO — стек; сегодня через `Deque` (`push` / `pop`), не через
  `Stack`.

---

## 7. Queue и Deque

`Queue` — очередь. Часто FIFO, но не всегда: `PriorityQueue` достаёт не «первый
добавленный», а элемент с высшим приоритетом (natural ordering или `Comparator`).
`null` в `PriorityQueue` нельзя.

`Deque` (Double Ended Queue) расширяет `Queue` и умеет вставку/извлечение с обоих концов.
На нём делают и очередь, и стек.

Почему `LinkedList` реализует и `List`, и `Deque`: у двусвязного списка дешёвые операции на
концах, это хорошо ложится на контракт `Deque`.

Для очереди/стека в однопотоке предпочтителен `ArrayDeque`: нет лишних `Node`, нет
синхронизации `Stack`/`Vector`.

---

## 8. Set

`Set` — без дубликатов (по `equals` / правилам конкретной реализации).

| Класс | Основа | Порядок |
|-------|--------|---------|
| `HashSet` | `HashMap` (элемент = ключ, значение — заглушка) | не гарантируется |
| `LinkedHashSet` | `LinkedHashMap` | порядок вставки (insertion-order) |
| `TreeSet` | красно-чёрное дерево (`TreeMap`) | sorted / `Comparator` |
| `EnumSet` | bit vector для одного `Enum` | порядок объявления enum |

`TreeSet`: сложность основных операций O(log n). Даже если класть элементы «по возрастанию»,
дерево само балансируется — преимущества не пропадают.

`EnumSet` компактнее и обычно быстрее `HashSet` для enum: хранит биты, а не хэш-таблицу.
Есть удобные фабрики: `EnumSet.of(...)`, `allOf`, `noneOf`, `range`.

---

## 9. Map и HashMap

### Зачем HashMap, если есть Hashtable

`Hashtable` синхронизирован, не допускает `null` ключ/значение, API старый (`Enumeration`).
`HashMap` быстрее в однопотоке, допускает один `null`-ключ и любые `null`-значения.
`Hashtable` — legacy.

Для многопотока — не `Collections.synchronizedMap(new HashMap<>())` как первый выбор, а
`ConcurrentHashMap` (с оговорками по `null` и семантике).

### Как устроен HashMap

Реализация — метод цепочек (separate chaining), не открытая адресация.

1. Есть таблица «корзин» (buckets) — массив.
2. У ключа считают `hashCode`, перемешивают биты, выбирают индекс корзины.
3. В корзине — цепочка узлов (`Node`). При коллизии новый узел добавляется в цепочку
   (если ключ уже есть по `equals` — значение заменяется).
4. С Java 8 длинная цепочка при выполнении условий превращается в красно-чёрное дерево
   (`TreeNode`): порог treeify обычно 8, untreeify — 6; дерево имеет смысл при достаточной
   ёмкости таблицы.

Параметры конструктора:

- `initialCapacity` — начальное число корзин (по умолчанию 16; округляется вверх до степени
  двойки);
- `loadFactor` — коэффициент загрузки (по умолчанию 0.75).

Когда `size > capacity * loadFactor`, таблица увеличивается примерно в 2 раза, элементы
перераспределяются (resize / rehash).

Средний случай `get` / `put` — O(1). Это **не жёсткая гарантия**: при плохом распределении
хешей до Java 8 худший случай O(n), с деревьями — около O(log n) на корзину.

Даже при разных `hashCode()` все ключи могут попасть в одну корзину, если после внутренней
функции индекса индексы совпадут (на практике реже, но идея «вырождения» важна).

### equals и hashCode

- `hashCode` выбирает корзину;
- `equals` отличает ключи внутри корзины.

Контракт: равные по `equals` объекты обязаны иметь одинаковый `hashCode`.

Ключ должен быть стабильным: если после `put` изменить поле, участвующее в `hashCode` /
`equals`, элемент можно «потерять» при `get` и при resize. Демо:
[MutableKeyHashMapDemo.java](code/MutableKeyHashMapDemo.java).

Почему `byte[]` плохой ключ: у массива `equals` / `hashCode` от `Object` — по идентичности
ссылки, не по содержимому. Другой массив с теми же байтами ключ не найдёт.

`hashCode()` возвращает `int` → 2³² возможных значений.

При `put` обычно создаётся один новый узел (`Node` / `TreeNode`). При resize массив и
перелинковка — отдельно.

Как сравнивают ключи при treeify, если нет `Comparable`: сначала хеши; если оба
`Comparable` одного класса — `compareTo`; иначе tie-break через имя класса /
`System.identityHashCode`. Поэтому дерево строится и без `Comparable` у ключа.

### Обход Map

```java
for (K key : map.keySet()) { ... }
for (V value : map.values()) { ... }
for (Map.Entry<K, V> e : map.entrySet()) { ... } // обычно лучший вариант для пар
```

---

## 10. Особые Map: LinkedHashMap, TreeMap, WeakHashMap, IdentityHashMap

### LinkedHashMap

От `HashMap` — корзины и O(1) доступ.  
От связного списка — двусвязный порядок обхода.

По умолчанию — insertion-order. Если в конструкторе `accessOrder = true` — access-order:
при `get`/`put` элемент уезжает в конец. На этом делают простой LRU-кэш через
`removeEldestEntry`.

Демо: [LruCacheDemo.java](code/LruCacheDemo.java).

Нюанс: повторный `put` того же ключа сам по себе порядок вставки не «обновляет» как новый
элемент (для access-order важен доступ).

### SortedMap / TreeMap

«Сортированность» видна не только в `toString`: итерация, `firstKey` / `lastKey`,
`subMap` / `headMap` / `tailMap`, у `NavigableMap` — `ceilingKey`, `floorKey` и т.д.

### WeakHashMap

Ключи хранятся через слабые ссылки (WeakReference). Если на ключ не осталось сильных
(и soft) ссылок, запись может исчезнуть после GC.

Типичный сценарий: ассоциировать с объектами доп. данные, не мешая собрать сами объекты.

Почему не PhantomHashMap: `PhantomReference.get()` всегда `null` — как ключ это бесполезно.
Soft-вариант бывает в библиотеках (например, Guava / Commons), в JDK готового
`SoftHashMap` нет.

### IdentityHashMap

Сравнивает ключи через `==` и `System.identityHashCode`, а не `equals` / обычный
`hashCode`. Нужна там, где важна идентичность объекта (графы при сериализации/клонировании,
топтология ссылок), а не бизнес-равенство.

Демо: [IdentityHashMapDemo.java](code/IdentityHashMapDemo.java).

---

## 11. Синхронизация и только для чтения

Синхронизированные обёртки:

```java
List<String> list = Collections.synchronizedList(new ArrayList<>());
Map<K, V> map = Collections.synchronizedMap(new HashMap<>());
```

Обход такой коллекции всё равно нужно синхронизировать вручную на самой коллекции.
Для concurrent-сценариев предпочтительнее `java.util.concurrent.*`.

Только для чтения:

```java
List<T> ro = Collections.unmodifiableList(list);
Set<T> ro = Collections.unmodifiableSet(set);
Map<K, V> ro = Collections.unmodifiableMap(map);
```

Это view: изменение исходной коллекции будет видно; прямые мутации через view дают
`UnsupportedOperationException`. С Java 9+ удобны ещё `List.of` / `Set.of` / `Map.of` —
неизменяемые копии с фиксированным содержимым.

```java
List<Integer> empty = Collections.emptyList();
empty.add(1); // UnsupportedOperationException
```

---

## 12. Сложность и выбор коллекций

Опора на разбор
[«Коллекции в Java: алгоритмическая сложность и как не тормозить код»](https://habr.com/ru/companies/otus/articles/994152/)
(OTUS / Habr).

### Big O коротко

Big O (асимптотическая сложность) показывает, как растёт время (или память) операции при
росте числа элементов `n`. Это не миллисекунды, а порядок роста:

- O(1) — почти не зависит от `n` (в среднем / амортизированно);
- O(log n) — растёт медленно (деревья);
- O(n) — линейно с размером.

Для коллекций это «дорожная карта»: что можно делать часто, а что — только осознанно.

### Сводная таблица (типичный / средний случай)

| Структура | get / contains по ключу-индексу | add «в конец» / put | add / remove «в середине» | Порядок |
|-----------|----------------------------------|---------------------|---------------------------|---------|
| `ArrayList` | get по индексу O(1); contains O(n) | амортизированно O(1) | O(n) сдвиг | индексный |
| `LinkedList` | O(n) | концы O(1) | по индексу O(n); через `ListIterator` на месте O(1) | связный |
| `ArrayDeque` | нет индексного List-API | концы O(1) | — | очередь/стек |
| `HashSet` / `HashMap` | среднее O(1) | среднее O(1) | — | нет |
| `LinkedHashSet` / `LinkedHashMap` | среднее O(1) | среднее O(1) | — | insertion / access |
| `TreeSet` / `TreeMap` | O(log n) | O(log n) | — | sorted |
| `EnumSet` / `EnumMap` | O(1) | O(1) | — | порядок enum |

Оговорки:

- у `HashMap`/`HashSet` O(1) **не гарантия**: плохой `hashCode` → вырождение; с Java 8
  длинная корзина → дерево ≈ O(log n);
- grow / resize — отдельные O(n) события; амортизация спасает средний `add`/`put`.

### Как выбрать (упрощённая схема)

```text
Нужен ключ → значение?
  да → нужен порядок?
         нет  → HashMap (EnumMap, если ключ enum)
         insertion/access → LinkedHashMap
         sorted / диапазоны → TreeMap
  нет → уникальность элементов?
         да → нужен порядок?
                нет → HashSet (EnumSet для enum)
                insertion → LinkedHashSet
                sorted → TreeSet
         нет → нужен индексный доступ / список?
                да  → почти всегда ArrayList
                очередь/стек по концам → ArrayDeque
                (LinkedList — редко)
```

Практические якоря из статьи:

- список «по умолчанию» — `ArrayList`;
- очередь/дек — `ArrayDeque`, не `LinkedList`;
- частый `contains` по множеству — `HashSet`, не `List`;
- нужен sorted / `subSet` / `headMap` — `Tree*`, ценой O(log n).

### Массив вместо коллекции

Иногда коллекция не нужна:

| Когда массив лучше | Почему |
|--------------------|--------|
| Фиксированный размер, горячий цикл | нет grow, меньше обёрток метода |
| Примитивы (`int[]`, `double[]`) | нет boxing; `ArrayList<Integer>` жрёт заметно больше |
| Предсказуемая память | нет resize / rehash |

Минусы массива: нет `contains`/`remove` из коробки, нельзя «просто вырасти».

Рабочий приём: внутри критичного метода — массив/примитивы; наружу — `List` /
`List.of` / stream. Для примитивных коллекций без boxing смотрят fastutil, Eclipse
Collections и аналоги.

---

## 13. Практика и полезные приёмы

### Правила, которые реально экономят CPU и баги

1. **Тип переменной — интерфейс**, создание — реализация:

```java
List<String> list = new ArrayList<>(); // не ArrayList<String> list = ...
Map<String, User> map = new HashMap<>();
```

Так проще сменить реализацию без правки всего кода.

2. **Пустой результат** — `Collections.emptyList()` / `emptySet()` / `emptyMap()` (или
   `List.of()`), а не `null` и не `new ArrayList<>()` «на всякий». Помнить: они
   неизменяемые.

3. **Задавать ожидаемый размер**, если он известен:

```java
List<Integer> list = new ArrayList<>(1_000_000);
Map<String, User> map = new HashMap<>(expectedSize * 2); // запас под loadFactor ≈ 0.75
```

Меньше grow / rehash — меньше копирований.

4. **Удаление при обходе** — `iterator.remove()` или `list.removeIf(...)`, не
   `list.remove` внутри for-each (CME). См. [FailFastDemo.java](code/FailFastDemo.java).

5. Для `TreeSet` / `TreeMap` / `Collections.sort` — корректный `Comparable` или явный
   `Comparator`. Иначе порядок и уникальность в дереве будут «сюрпризом».

6. **Профилировать**. Big O — карта, не секундомер. Для микрозамеров — прогрев JVM и
   среднее; для серьёзных — JMH. Иногда «теоретически хуже» структура выигрывает на кэше
   процессора.

### Мелочи API

Симметрическая разность (элементы ровно из одной из двух коллекций):

```java
<T> Collection<T> symmetricDifference(Collection<T> a, Collection<T> b) {
    Collection<T> result = new ArrayList<>(a);
    result.addAll(b);
    Collection<T> intersection = new ArrayList<>(a);
    intersection.retainAll(b);
    result.removeAll(intersection);
    return result;
}
```

Одной строкой:

```java
Object[] array = collection.toArray();
List<E> middle = list.subList(3, list.size() - 3); // view, не копия!
List<E> list = new ArrayList<>(set);
Set<E> set = new HashSet<>(list);
Set<K> keys = new HashSet<>(map.keySet());
```

`subList` — представление (view) исходного списка. Изменения отражаются в обе стороны;
структурные изменения исходного списка вне view делают view неопределённым.

Способы обхода списка: for-each, явный `Iterator` / `ListIterator`, индексный `for`,
`list.forEach`, Stream API. Для `LinkedList` индексный `for` с `get(i)` — плохая идея
(каждый `get` — O(n)).

---

## 14. Null в коллекциях

Единого правила «коллекции принимают / не принимают `null`» нет. Это контракт
конкретной реализации. На собесе и в проде важно знать таблицу и типичные ловушки.

### Кто принимает `null`

| Структура | Элемент / ключ / значение |
|-----------|---------------------------|
| `ArrayList`, `LinkedList`, `Vector` | элементы — да, сколько угодно |
| `CopyOnWriteArrayList` | да |
| `HashSet`, `LinkedHashSet` | один `null` (как один `null`-ключ в `HashMap`) |
| `HashMap`, `LinkedHashMap`, `IdentityHashMap` | один `null`-ключ; значений `null` — сколько угодно |
| `WeakHashMap` | `null`-ключ поддерживается (sentinel); `null`-значения — да |
| `TreeMap` | ключ `null` при natural ordering — **нет**; значение `null` — да |
| `Hashtable` | ключи и значения — **нет** |
| `ConcurrentHashMap`, `ConcurrentSkipListMap` | ключи и значения — **нет** |
| `TreeSet`, `PriorityQueue`, `ArrayDeque`, `EnumSet` | **нет** (обычно NPE) |

`LinkedList` как `List`/`Deque` `null` принимает; использовать его как очередь с `null`
внутри — плохая идея (см. ниже).

### Хитрые моменты

**1. `get` вернул `null` — ключа нет или значение было `null`?**

```java
map.put("a", null);
map.get("a"); // null — значение есть
map.get("b"); // null — ключа нет
```

Различить: `containsKey(...)`. Именно из‑за этой двусмысленности `ConcurrentHashMap`
запрещает `null` (в многопотоке нельзя надёжно отличить «нет ключа» от «value == null»).

**2. Фабрики Java 9+ жёстче старых**

```java
Collections.singletonList(null); // ок
List.of(null);                   // NPE
Set.of(null);                    // NPE
Map.of("k", null);               // NPE
Arrays.asList((String) null);    // ок
```

**3. `TreeSet` / `TreeMap` и сравнение**

При natural ordering (`Comparable`) `null` нельзя → NPE.  
С `Comparator`, который умеет `null` (`nullsFirst` / `nullsLast`), `null`-ключ/элемент
может быть допустим. По умолчанию считать «нельзя».

Уникальность в `TreeSet` определяется через `compare` / `compareTo`, не только через
`equals`.

**4. Очереди и `null`**

`ArrayDeque` и `PriorityQueue` бросают NPE на `null`.  
У `Queue` методы `poll()` / `peek()` возвращают `null`, если очередь пуста. Если положить
`null` в `LinkedList`-очередь, «пусто» и «вынули null» не отличить. Спека `Queue` как раз
поэтому обычно запрещает `null` как элемент.

**5. `remove(null)` vs `remove(int)`**

```java
list.remove(null);                 // поиск по equals(null)
list.remove(1);                    // это remove(int index)!
list.remove(Integer.valueOf(1));   // удаление по значению
```

С `null` индексной перегрузки нет, но с числами путаница классическая.

**6. Сортировка**

```java
Collections.sort(list); // NPE, если в списке есть null
list.sort(Comparator.nullsLast(Comparator.naturalOrder()));
```

**7. Повторный `HashSet.add(null)`**

Вернёт `false` — обычное правило `Set` для дубликата, не «особый null».

### Практическое правило

- Однопоточный map и нужен `null` в value → `HashMap` ок, всегда думайте про `containsKey`.
- Многопоточный map → `ConcurrentHashMap`; «нет значения» моделируйте отдельно (нет ключа,
  sentinel, свой тип результата) — не через `null`.
- Сортированные структуры → без `null` или явный null-safe `Comparator`.
- Очередь / стек → `ArrayDeque`, без `null`.
- На границе API часто лучше пустая коллекция, чем `null`-поле, и без `null` внутри.

---

## 15. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [FailFastDemo.java](code/FailFastDemo.java) | CME при удалении в for-each; безопасный `iterator.remove()` |
| [ArrayListBulkRemoveDemo.java](code/ArrayListBulkRemoveDemo.java) | `remove` в цикле vs `subList.clear()` |
| [MutableKeyHashMapDemo.java](code/MutableKeyHashMapDemo.java) | потерянный элемент при мутации ключа |
| [IdentityHashMapDemo.java](code/IdentityHashMapDemo.java) | `==` vs `equals` как равенство ключей |
| [LruCacheDemo.java](code/LruCacheDemo.java) | LRU на `LinkedHashMap` + `removeEldestEntry` |

---

## 16. Вопросы для собеседования и ответы

### 1. Почему `Map` не является `Collection`?

`Collection` — набор элементов с операциями вроде `add(e)`, `contains(e)`. `Map` — набор
соответствий ключ → значение: другой контракт, другие операции (`put`, `get`, `entrySet`).
`keySet()` / `values()` / `entrySet()` уже дают коллекции-представления, но сама `Map` в
иерархию `Collection` не входит.

### 2. Чем fail-fast отличается от итераторов concurrent-коллекций?

Fail-fast (`ArrayList`, `HashMap`, …) детектит структурные изменения через `modCount` и
бросает `ConcurrentModificationException`. У `CopyOnWriteArrayList` итератор идёт по
снимку. У `ConcurrentHashMap` обход weakly consistent: CME нет, полной «замороженной»
картины на весь обход тоже нет. Слово fail-safe в учебниках часто слишком грубое.

Демо: [FailFastDemo.java](code/FailFastDemo.java).

### 3. Чем ArrayList отличается от LinkedList? Что обычно брать? Что быстрее? Куда класть 1 млн элементов?

Это один блок: на собесе часто маскируют один и тот же вопрос.

`ArrayList` — список на массиве: `get(i)` O(1), `add` в конец амортизированно O(1),
вставка/удаление в середине O(n) из‑за сдвига, мало памяти на элемент.  
`LinkedList` — двусвязный список: концы O(1), доступ/вставка по индексу O(n) из‑за поиска
узла, каждый элемент — объект `Node`.

**Что обычно используете?** В ~90% случаев `ArrayList`. `LinkedList` — редко; для
очереди/стека сегодня чаще `ArrayDeque`.

**Что быстрее?** Встречный вопрос: *какие операции?* Без контекста односложный ответ —
ловушка.

**Добавить 1 млн элементов — что выбрать?** Информации мало. Уточнять: куда вставляют
(конец / середина / начало)? что потом делают (`get(i)`, итерация, удаления)? есть ли
лимит памяти? Нужен ли индексный доступ? Часто ответ снова `ArrayList` с
`new ArrayList<>(1_000_000)`, но это не догма.

Подробности: [раздел 6](#6-list-arraylist-linkedlist-vector),
[картинки LinkedList](https://habr.com/ru/articles/127864/).

### 4. Зачем в ArrayList отдельно `size`, если есть `elementData.length`? Как растёт список? Что с capacity при remove?

`length` — capacity (сколько слотов выделено). `size` — сколько элементов реально лежит.
Между ними обычно запас.

При `remove` элементы «правее» сдвигаются влево (`System.arraycopy`), capacity **не**
уменьшается. Автосжатия нет — только явный `trimToSize()`. Обнуление
`elementData[--size] = null` нужно, чтобы GC мог собрать удалённый объект.

Про default capacity: в старых JDK `new ArrayList<>()` сразу выделял массив на 10. В
современных (Java 8+ / 17 / 21) конструктор держит пустой shared-массив, а 10 появляется при
**первом** `add`. Рост дальше ≈ ×1.5 (`old + (old >> 1)`), а не обязательно старая формула
`(old * 3) / 2 + 1` из статей 2011–2012. Если размер известен — `new ArrayList<>(n)`.
Визуально: [Habr про ArrayList](https://habr.com/ru/articles/128269/).

### 5. Как эффективно удалить диапазон из середины ArrayList?

Не вызывать `remove(i)` в цикле (каждый раз сдвигает хвост). Лучше
`list.subList(from, to).clear()` — один компактный сдвиг через `System.arraycopy`.
Демо: [ArrayListBulkRemoveDemo.java](code/ArrayListBulkRemoveDemo.java).

### 6. Как устроен HashMap и почему method of chaining?

Массив корзин + цепочки (с Java 8 ещё деревья в длинных корзинах). Chaining проще масштабирует
неизвестный размер и удаления, чем открытая адресация; load factor может быть > 1 на уровне
«элементов на корзину». Цена — объекты узлов и худший cache locality. Открытая адресация
компактнее, но болезненнее при высоком заполнении и удалениях.

### 7. Будет ли HashMap работать, если у всех ключей один hashCode?

Да. Все попадут в одну корзину (или в дерево этой корзины). Средний O(1) пропадет, останется
обход цепочки/дерева. Плюс растущая таблица всё равно будет resize'иться по `size`, но
распределение по корзинам не улучшится, пока хеши одинаковые.

### 8. Когда элемент «теряется» в HashMap?

Ключ — изменяемый объект. После `put` поменяли поле, от которого зависят `hashCode`/`equals`.
`get` ищет уже в другой корзине или не находит по `equals`. После resize ситуация может
стать ещё хуже. Демо: [MutableKeyHashMapDemo.java](code/MutableKeyHashMapDemo.java).

### 9. Чем IdentityHashMap отличается от HashMap?

Равенство ключей по ссылке (`==`), хеш — `System.identityHashCode`. Нужна для задач, где
два equal-объекта должны оставаться разными ключами. Обычное требование «неизменяемый ключ
для корректного equals/hashCode» здесь другое: важен стабильный identity объекта.

### 10. Как из LinkedHashMap сделать LRU-кэш?

`accessOrder = true` и переопределить `removeEldestEntry`, чтобы удалять eldest, когда
`size() > MAX`. При доступе элемент уезжает в конец, редко используемые скапливаются в
начале. Демо: [LruCacheDemo.java](code/LruCacheDemo.java).

### 11. Чем TreeSet отличается от HashSet?

`HashSet` — хеш-таблица, порядок не обещан, среднее O(1). `TreeSet` — красно-чёрное дерево,
элементы упорядочены, операции O(log n), нужен `Comparable` или `Comparator`. Добавление
«уже отсортированной» последовательности дерево не ломает — оно балансируется.

### 12. Зачем EnumSet, если есть HashSet?

`EnumSet` хранит enum'ы как биты: мало памяти, быстрые bulk-операции, порядок — как в
объявлении enum. Для множества констант одного enum это правильный default вместо
`HashSet`.

### 13. Чем заменить Stack и Vector?

Стек/очередь → `ArrayDeque` (интерфейс `Deque`). Список → `ArrayList`. Нужна concurrency →
коллекции из `java.util.concurrent`, а не «все методы synchronized» у `Vector`.

### 14. Почему LinkedList реализует и List, и Deque?

Потому что двусвязный список естественно поддерживает операции на обоих концах за O(1), а
это контракт `Deque`. Одновременно он остаётся `List` с позиционным API (дорогой по
индексу).

### 15. Что будет, если вызвать `iterator.remove()` без `next()`?

`IllegalStateException`. Удалять можно только элемент, возвращённый последним `next()`
(или эквивалентной позицией у `ListIterator`). Если же коллекцию изменили мимо итератора,
дальше обычно `ConcurrentModificationException`.

### 16. Как быстро выбрать коллекцию и когда хватит обычного массива?

Смотреть на доступ и инварианты, не на «привычку к ArrayList»:

- ключ → значение → `HashMap` / `LinkedHashMap` / `TreeMap` / `EnumMap`;
- уникальность → `HashSet` / `LinkedHashSet` / `TreeSet` / `EnumSet`;
- список с индексом → `ArrayList`;
- очередь/стек → `ArrayDeque`.

Частый `contains` по `List` на больших данных — типичный тормоз: нужен `HashSet`. Если
размер фиксирован и важны примитивы/hot path — массив (`int[]`) или примитивные коллекции
сторонних библиотек; наружу можно отдать `List`. Сводная таблица и схема — в
[разделе 12](#12-сложность-и-выбор-коллекций); развёрнуто также в
[статье OTUS](https://habr.com/ru/companies/otus/articles/994152/).

### 17. Как устроен HashMap? Какое начальное число корзин?

Массив корзин (buckets) + в каждой корзине цепочка узлов (с Java 8 при длинной цепочке —
красно-чёрное дерево). `hashCode` ключа → индекс корзины; внутри корзины ключи сравнивают
через `equals`. При совпадении ключа значение заменяют.

В конструкторе по умолчанию начальная capacity таблицы — **16** (степень двойки). Можно
задать своё значение в `HashMap(initialCapacity)` / с `loadFactor`. Подробнее —
[раздел 9](#9-map-и-hashmap).

### 18. Какая сложность get/put у HashMap? Гарантирует ли она O(1)?

В среднем при хорошем распределении хешей — O(1). **Жёсткой гарантии нет.**

Если `hashCode` всегда один и тот же, структура вырождается в одну корзину: до Java 8 это
O(n) по списку, с Java 8+ после treeify — около O(log n) на корзину. Даже при «честной»
хеш-функции в худшем случае нельзя обещать константу для каждого вызова. На собесе ждут
именно эту оговорку, а не голое «всегда O(1)».

### 19. Какова роль equals() и hashCode() в HashMap?

- `hashCode` — выбрать корзину;
- `equals` — отличить ключи внутри корзины (и решить, заменять ли value при `put`).

Контракт: если `a.equals(b)`, то `a.hashCode() == b.hashCode()`. Ломаете контракт —
ломаете map/set.

### 20. Сколько различных значений может вернуть hashCode()?

Сигнатура `int hashCode()` → диапазон `int`, то есть **2³²** различных значений
(от `Integer.MIN_VALUE` до `Integer.MAX_VALUE`).

### 21. Как и когда увеличивается число корзин в HashMap?

Есть `capacity` (размер таблицы) и `loadFactor` (по умолчанию 0.75). Порог
`threshold ≈ capacity * loadFactor`. Когда число **элементов** (`size`) превышает порог,
таблица растёт примерно **в 2 раза**, элементы перераспределяются (resize / rehash).

Уточнение к старым формулировкам «число занятых корзин»: в реализации ориентир — `size`
(записей), не «сколько корзин непустые».

### 22. Почему нельзя (бессмысленно) использовать byte[] как ключ в HashMap?

У массивов не переопределены `equals` / `hashCode` под содержимое: сравнение и хеш — как у
`Object` (по идентичности ссылки). Другой массив с теми же байтами — другой ключ.
Достать значение можно только той же ссылкой, что клали в `put`. Для содержимого нужен
свой тип-ключ или, например, обёртка с корректными `equals`/`hashCode`.

### 23. Чем TreeSet отличается от HashSet? Что будет, если класть элементы по возрастанию?

`HashSet` — на `HashMap`, порядка нет, среднее O(1).  
`TreeSet` — красно-чёрное дерево (`TreeMap`), порядок natural / `Comparator`, операции
O(log n).

Ловушка: «это же бинарное дерево, по возрастанию получится вырожденный список». Нет: RB-дерево
**балансируется**, порядок вставки не уничтожает O(log n). Уникальность в `TreeSet` идёт
через сравнение (`compare`/`compareTo`), не через `equals` в одиночку — ещё один частый
нюанс на собесе.

### 24. Какие «маски» вопросов по коллекциям чаще всего встречают?

По мотивам [статьи sphinks](https://habr.com/ru/articles/162017/):

| Формулировка | На что реально проверяют |
|--------------|--------------------------|
| «Что быстрее: ArrayList или LinkedList?» | Понимание операций, а не бренд класса |
| «Что обычно используете?» | Default = ArrayList + когда LinkedList/Deque |
| «Куда добавить 1 млн элементов?» | Умение задавать уточняющие вопросы |
| «Гарантирует ли HashMap O(1)?» | Роль hashCode / вырождение / (с Java 8) дерево |
| «Потеряли элемент в HashMap» | Мутабельный ключ |
| «TreeSet по возрастанию» | Балансировка RB-дерева |

Хороший ответ часто начинается с уточнения сценария, а не с угадывания одного слова.

### 25. Какие коллекции принимают `null`, а какие нет?

См. таблицу в [разделе 14](#14-null-в-коллекциях). Коротко:

- списки (`ArrayList`, `LinkedList`) и `HashMap`/`HashSet` — в основном да;
- `TreeSet`, `PriorityQueue`, `ArrayDeque`, `ConcurrentHashMap`, `Hashtable`, `EnumSet` —
  нет (NPE);
- у `TreeMap` нельзя `null`-ключ при natural ordering, `null`-value можно.

### 26. Почему ConcurrentHashMap запрещает `null`, а HashMap — нет?

У `HashMap.get(key)` `null` означает и «ключа нет», и «значение было null». В однопотоке
это разруливают через `containsKey`. В concurrent-сценарии такая двусмысленность опасна и
дорога; `ConcurrentHashMap` просто запрещает `null` для ключей и значений.

### 27. Чем `List.of(null)` отличается от `Collections.singletonList(null)`?

`singletonList(null)` — валидный неизменяемый список из одного `null`.  
`List.of(null)` (и `Set.of` / `Map.of` с null) — сразу `NPE`. Фабрики Java 9+ жёстче к
`null`, чем старые `Collections.*`.
