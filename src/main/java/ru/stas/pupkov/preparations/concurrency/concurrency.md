# Многопоточность (Concurrency / Multithreading)

Java 17+. Заметка для изучения и подготовки к собеседованиям.
Учитывает модель памяти JSR-133 (Java 5+) и актуальные API `java.util.concurrent`.
Где уместно — заметки про virtual threads (Java 21).

## Содержание

1. [Модель памяти Java](#1-модель-памяти-java)
2. [Базовые термины](#2-базовые-термины)
3. [Конкуренция и параллелизм](#3-конкуренция-и-параллелизм)
4. [Процесс, поток, green / virtual threads](#4-процесс-поток-green--virtual-threads)
5. [Создание потоков и жизненный цикл](#5-создание-потоков-и-жизненный-цикл)
6. [Мониторы и синхронизация](#6-мониторы-и-синхронизация)
7. [wait / notify, sleep, yield, join](#7-wait--notify-sleep-yield-join)
8. [volatile, Atomic и CAS](#8-volatile-atomic-и-cas)
9. [Race condition, deadlock, livelock](#9-race-condition-deadlock-livelock)
10. [Прерывание и остановка потока](#10-прерывание-и-остановка-потока)
11. [Пулы потоков и Executor](#11-пулы-потоков-и-executor)
12. [Stack, heap, ThreadLocal, обмен данными](#12-stack-heap-threadlocal-обмен-данными)
13. [Lock, ReadWriteLock, Semaphore](#13-lock-readwritelock-semaphore)
14. [Синхронизаторы и Fork/Join](#14-синхронизаторы-и-forkjoin)
15. [Singleton, immutability, практика](#15-singleton-immutability-практика)
16. [Демо-код](#16-демо-код)
17. [Вопросы для собеседования и ответы](#17-вопросы-для-собеседования-и-ответы)

---

## 1. Модель памяти Java

Модель памяти Java (Java Memory Model, JMM) — часть семантики языка: какие записи одного
потока обязан увидеть другой поток и какие переупорядочивания (reordering) допустимы.

Это **не** описание «у каждого потока есть своя рабочая память как отдельный RAM».
Рабочая память / кэши / регистры — педагогическая метафора. Реальность: компилятор, JIT,
CPU store buffer и кэш-когерентность могут откладывать и переставлять операции, пока это
не нарушает happens-before.

### 1.1. Happens-before

Если действие *X* связано отношением happens-before с действием *Y*, то результат *X*
виден *Y*, и *X* упорядочен перед *Y* с точки зрения наблюдателя (visibility + ordering).

Ключевые правила (JLS §17.4):

| Правило | Смысл |
|--------|--------|
| Program order | В одном потоке предшествующее действие happens-before последующего |
| Monitor unlock → lock | `unlock(M)` happens-before последующий `lock(M)` |
| Volatile write → read | запись `volatile` happens-before последующее чтение того же поля |
| Thread start | `start()` happens-before первое действие в `run()` нового потока |
| Thread join | последнее действие в потоке happens-before возврат из `join()` / `isAlive()==false` |
| Interrupt | `interrupt()` happens-before обнаружение прерывания в целевом потоке |
| Transitivity | HB транзитивно |

HB связывает **конкретные** действия на **одном и том же** мониторе / volatile-поле.
Третий поток без своей цепочки HB ничего «бесплатно» не получает.

Классический паттерн «данные + флаг готовности» на `volatile`:

```java
class ReadyPayload {
    private int data;                 // не volatile
    private volatile boolean ready;   // публикация

    void publish(int value) {
        data = value;     // (1)
        ready = true;     // (2) volatile write: всё до (2) «всплывает» для читателя
    }

    int consume() {
        if (!ready) {     // (3) volatile read
            return -1;
        }
        return data;      // (4) видит запись (1), т.к. (2) HB (3) и транзитивность
    }
}
```

`start` / `join` тоже строят HB без явного `synchronized`:

```java
int[] box = {0};
Thread worker = new Thread(() -> box[0] = 42);
worker.start();   // всё, сделанное в main до start, видно в worker
worker.join();    // всё, сделанное в worker до конца run, видно в main после join
System.out.println(box[0]); // 42 — гарантировано
```

### 1.2. Visibility, reordering, final

- **Visibility** — когда запись одного потока становится видимой другому.
- **Reordering** — компилятор/CPU могут менять порядок, пока intra-thread as-if-serial
  сохраняется; другой поток без HB может увидеть «странный» порядок.
- **`final`** — после корректного завершения конструктора значения `final`-полей видны
  другим потокам без доп. синхронизации (safe initialization для immutable).
  Публиковать `this` из конструктора до завершения — ломает эту гарантию.
  Менять `final` через reflection после публикации — undefined behavior по сути.

Опасная публикация `this`:

```java
class Broken {
    private final int x;

    Broken(List<Broken> leak) {
        leak.add(this); // this ещё не до конца сконструирован
        x = 1;
    }
}
```

Демо visibility: [VolatileVisibilityDemo.java](code/VolatileVisibilityDemo.java).

---

## 2. Базовые термины

| Термин | Рабочее определение |
|--------|---------------------|
| Ordering | Ограничения на наблюдаемый порядок операций между потоками |
| As-if-serial semantics | Внутри **одного** потока результат как при последовательном исполнении исходного порядка (с оговорками про data races) |
| Sequential consistency | Глобальный порядок, согласованный с program order каждого потока — **сильнее**, чем JMM даёт по умолчанию |
| Visibility | Видимость записей между потоками |
| Atomicity | Операция неделима для наблюдателя: либо сделана целиком, либо нет |
| Happens-before | Частичный порядок JMM, дающий visibility + ordering |
| Mutual exclusion | В критической секции не больше одного потока |
| Safe publication | Передача ссылки так, чтобы получатель видел полностью инициализированное состояние |

Safe publication в Java:

```java
// 1) static initializer
static final Config CONFIG = loadConfig();

// 2) volatile / AtomicReference
volatile Service service;
service = new Service(...);

// 3) один и тот же lock на publish и consume
synchronized (lock) { shared = new State(...); }
synchronized (lock) { use(shared); }

// 4) final-поля + корректный конструктор (immutable)
final class Point {
    private final int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
}

// 5) concurrent-коллекция
BlockingQueue<Task> q = new LinkedBlockingQueue<>();
q.put(task);          // publisher
Task t = q.take();    // consumer видит полностью записанный task
```

Потокобезопасность (thread safety) — инварианты класса сохраняются при любом
чередовании вызовов из нескольких потоков (при соблюдении контракта API).

---

## 3. Конкуренция и параллелизм

- **Конкуренция (concurrency)** — структура программы с несколькими логическими потоками
  управления. Даже на одном ядре задачи *чередуются*. Результат часто недетерминирован
  без синхронизации.
- **Параллелизм (parallelism)** — одновременное выполнение на нескольких ядрах / CPU.
  Параллелизм — способ ускорить работу; конкуренция — способ структурировать
  одновременность (в т.ч. ожидание I/O).

```java
// Конкуренция без параллелизма: два потока на 1 CPU — чередование
// Параллелизм: тот же код на 8 ядрах может реально бежать одновременно

ExecutorService pool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors());
List<Future<Long>> futures = IntStream.range(0, 8)
        .mapToObj(i -> pool.submit(() -> heavyCompute(i)))
        .toList();
```

Java использует **вытесняющую многозадачность** (preemptive): планировщик ОС / runtime
может переключить поток без его согласия. Кооперативная модель (поток сам отдаёт CPU)
для platform threads не используется — один зависший поток не обязан «повесить» весь
процесс (хотя может держать lock и блокировать других).

---

## 4. Процесс, поток, green / virtual threads

**Процесс** — экземпляр программы с собственным адресным пространством и ресурсами ОС.
**Поток (thread)** — единица планирования внутри процесса; потоки одного процесса делят
heap и открытые ресурсы, у каждого свой стек (и служебные структуры).

**Green threads** — потоки, которые планирует runtime, а не ОС (один OS-thread на много
green). В классической JVM они ушли после Java 1.2 в пользу native (platform) threads.

С **Java 21** появились **virtual threads** (Project Loom): лёгкие потоки JVM, которые
mount/unmount на carrier (platform) threads. Это не возврат старых green threads один в один,
но идея «много логических потоков на мало OS-threads» снова актуальна. Для I/O-bound
нагрузки virtual threads часто снимают нужду в огромных пулах platform threads.

Способы запуска задачи:

```java
// 1) subclass Thread (редко нужен)
class Worker extends Thread {
    @Override public void run() { /* ... */ }
}
new Worker().start();

// 2) Runnable / lambda + Thread
new Thread(() -> doWork(), "worker-1").start();

// 3) Executor (предпочтительно для platform threads)
ExecutorService pool = Executors.newFixedThreadPool(4);
pool.execute(() -> doWork());
Future<Integer> f = pool.submit(() -> compute());

// 4) Java 21+: virtual thread
Thread.startVirtualThread(() -> doWork());
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> doWork());
}
```

`Thread` — управление и идентичность потока. `Runnable` — задача. Предпочтительнее
передавать задачу в executor, а не плодить `new Thread` вручную.

`start()` регистрирует новый поток; `run()` как обычный метод выполняется в **текущем**
потоке. Принудительно заставить планировщик немедленно исполнить поток нельзя — только
`start()` и дальше политика ОС/JVM.

Демо: [StartVsRunDemo.java](code/StartVsRunDemo.java).

---

## 5. Создание потоков и жизненный цикл

Актуальные состояния `Thread.State` (не путать с устаревшими схемами «Running отдельно»):

| State | Когда |
|-------|--------|
| `NEW` | создан, `start()` ещё не вызван |
| `RUNNABLE` | исполняется или готов к исполнению (в т.ч. waiting for CPU) |
| `BLOCKED` | ждёт входа в `synchronized` (монитор занят) |
| `WAITING` | бесконечное ожидание (`Object.wait()`, `join()`, `LockSupport.park()` …) |
| `TIMED_WAITING` | ожидание с таймаутом (`sleep`, `wait(timeout)`, `join(timeout)` …) |
| `TERMINATED` | `run()` завершён |

```java
Thread t = new Thread(() -> {});
System.out.println(t.getState()); // NEW
t.start();
// RUNNABLE / дальше по ходу выполнения
t.join();
System.out.println(t.getState()); // TERMINATED

t.start(); // IllegalThreadStateException — второй раз нельзя
```

Приоритет `Thread.setPriority` (1..10) — слабый намёк планировщику; на проде на него
редко опираются. Потоки-демоны (`setDaemon(true)` до `start`): JVM завершается, когда
не осталось non-daemon потоков.

```java
Thread cleaner = new Thread(() -> { /* background */ }, "cache-cleaner");
cleaner.setDaemon(true); // обязательно до start()
cleaner.start();
```

`Runnable` vs `Callable<V>`:

| | `Runnable` | `Callable<V>` |
|--|------------|---------------|
| Метод | `run()` | `call()` |
| Результат | `void` | `V` |
| Checked exceptions | нет | да |
| С executor | `execute` / `submit` | `submit` → `Future<V>` |

```java
ExecutorService pool = Executors.newSingleThreadExecutor();

pool.execute(() -> System.out.println("runnable"));

Future<Integer> future = pool.submit(() -> {
    if (fail) {
        throw new IOException("checked ok in call()");
    }
    return 42; // call() возвращает V, не Future
});
Integer value = future.get(); // блокируется; исключения — в ExecutionException

FutureTask<String> task = new FutureTask<>(() -> "done");
pool.execute(task);           // FutureTask сам Runnable
System.out.println(task.get());
pool.shutdown();
```

Непойманное исключение в потоке завершает поток. Можно поставить
`Thread.UncaughtExceptionHandler`:

```java
Thread t = new Thread(() -> { throw new RuntimeException("boom"); });
t.setUncaughtExceptionHandler((thread, ex) ->
        System.err.println(thread.getName() + " => " + ex));
t.start();
```

В thread pool исключения из `Runnable` часто «проглатываются», если не логировать /
не брать `Future.get()`.

---

## 6. Мониторы и синхронизация

У каждого объекта Java есть **монитор** (intrinsic lock): взаимное исключение + wait-set
(`wait` / `notify`). «Мьютекс» в разговоре часто = взаимное исключение; монитор Java богаче
простого mutex.

```java
class Counter {
    private int value;

    // монитор this
    public synchronized void inc() {
        value++;
    }

    // то же самое явно
    public void incExplicit() {
        synchronized (this) {
            value++;
        }
    }

    // монитор Class-объекта
    public static synchronized void staticInc() { /* ... */ }

    public static void staticIncExplicit() {
        synchronized (Counter.class) {
            /* ... */
        }
    }
}
```

Private lock — чтобы клиент не влез в вашу схему через `synchronized (instance)`:

```java
class SecureCounter {
    private final Object lock = new Object();
    private int value;

    void inc() {
        synchronized (lock) {
            value++;
        }
    }
}
```

Пока выполняется `static synchronized`, создавать экземпляры класса **можно**:
конструктор не требует монитора `Class`, если сам на нём не синхронизируется.

```java
assert Thread.holdsLock(lock); // диагностика в тестах / assert'ах
```

Синхронизация — не «чтобы потоки бежали параллельно», а чтобы согласовать доступ к
shared mutable state (mutual exclusion + memory effects через HB).

---

## 7. wait / notify, sleep, yield, join

`wait` / `notify` / `notifyAll` — методы `Object`, вызываются только при владении монитором
иначе `IllegalMonitorStateException`. Причина «только в synchronized» не в том, что методы
«сами не synchronized», а в контракте монитора: wait атомарно отпускает lock и ждёт в
wait-set; notify сигналит wait-set того же монитора.

```java
synchronized (lock) {
    while (!condition) {   // цикл: spurious wakeup + re-check
        lock.wait();
    }
    // использовать состояние
    lock.notifyAll();
}
```

Минимальный bounded buffer на мониторе:

```java
class MonitorBuffer<T> {
    private final Queue<T> q = new ArrayDeque<>();
    private final int capacity;

    MonitorBuffer(int capacity) { this.capacity = capacity; }

    synchronized void put(T v) throws InterruptedException {
        while (q.size() == capacity) {
            wait();
        }
        q.add(v);
        notifyAll();
    }

    synchronized T take() throws InterruptedException {
        while (q.isEmpty()) {
            wait();
        }
        T v = q.remove();
        notifyAll();
        return v;
    }
}
```

- `notify()` — будит одного ожидающего (кого — не специфицировано);
- `notifyAll()` — всех; безопаснее при нескольких условиях на одном мониторе
  (иначе возможен signal loss / missed wakeup при «не том» потоке).

`wait(timeout)` возвращается по сигналу, по таймауту или spurious — условие всё равно
проверяют в цикле.

| Метод | Lock | Назначение |
|-------|------|------------|
| `Object.wait` | отпускает монитор | ждать условия |
| `Thread.sleep` | **не** отпускает | пауза по времени |
| `Thread.yield` | не отпускает | намёк отдать CPU (слабо) |
| `Thread.join` | — | ждать завершения другого потока |

```java
synchronized (lock) {
    Thread.sleep(1000); // монитор НЕ отпускается — другие на этом lock ждут
}
```

Последовательность T1 → T2 → T3:

```java
Thread t1 = new Thread(this::step1, "T1");
Thread t2 = new Thread(this::step2, "T2");
Thread t3 = new Thread(this::step3, "T3");
t1.start(); t1.join();
t2.start(); t2.join();
t3.start(); t3.join();
```

Демо буфера: [WaitNotifyBoundedBufferDemo.java](code/WaitNotifyBoundedBufferDemo.java).

---

## 8. volatile, Atomic и CAS

`volatile`:

- visibility: чтение видит последнюю запись;
- ordering: нельзя переупорядочить обычные доступы «через» volatile read/write так, как
  запрещает JMM;
- **не** делает RMW (`i++`) атомарным.

```java
volatile boolean running = true; // ok как флаг остановки (если только пишут true→false)

volatile int count;
count++; // НЕ потокобезопасно: read + add + write
```

`AtomicInteger` и семья: атомарные RMW через CAS:

```java
AtomicInteger count = new AtomicInteger();
count.incrementAndGet();
count.compareAndSet(expected, next);

// типичный CAS-retry
AtomicReference<Node<T>> head = new AtomicReference<>();
Node<T> created = new Node<>(value, null);
Node<T> prev;
do {
    prev = head.get();
    created.next = prev;
} while (!head.compareAndSet(prev, created));
```

`weakCompareAndSet*` может вернуть `false` «впустую» (spurious failure) — нормально внутри
retry-цикла. С Java 9 memory effects разведены через `VarHandle`
(`Plain` / `Acquire` / `Release` / `Volatile`).

`synchronized` = mutual exclusion + memory effects по правилам монитора.
`transient` / `native` к concurrency не относятся (сериализация / JNI).

Демо: [RaceConditionAtomicDemo.java](code/RaceConditionAtomicDemo.java),
[CasStackDemo.java](code/CasStackDemo.java).

---

## 9. Race condition, deadlock, livelock

**Race condition** — корректность зависит от гонки расписания. Типичный check-then-act:

```java
// плохо: между contains и put другой поток уже мог положить ключ
if (!map.containsKey(key)) {
    map.put(key, create(key));
}

// лучше для ConcurrentHashMap
map.computeIfAbsent(key, this::create);
```

**Deadlock** — цикличное ожидание блокировок. Условия Коффмана: mutual exclusion,
hold-and-wait, no preemption, circular wait.

```java
// классика: разный порядок захвата
synchronized (a) {
    synchronized (b) { /* ... */ }
}
// в другом потоке:
synchronized (b) {
    synchronized (a) { /* ... */ }
}

// антидот 1: глобальный порядок
Object first = System.identityHashCode(a) < System.identityHashCode(b) ? a : b;
Object second = first == a ? b : a;
synchronized (first) {
    synchronized (second) { /* ... */ }
}

// антидот 2: tryLock с таймаутом + backoff
if (lockA.tryLock(100, MILLISECONDS)) {
    try {
        if (lockB.tryLock(100, MILLISECONDS)) {
            try { /* work */ } finally { lockB.unlock(); }
        }
    } finally { lockA.unlock(); }
}
```

Демо: [DeadlockDemo.java](code/DeadlockDemo.java).

**Livelock** — потоки не блокируются, но бесконечно «уступают» друг другу:

```java
// схематично: оба вежливо освобождают ресурс и пробуют снова без backoff
while (!resource.tryAcquire()) {
    resource.yieldToOther(); // оба крутятся здесь вечно
}
```

**Busy spin** — крутить цикл вместо park/sleep на очень коротких ожиданиях (low-latency).
Иначе жжёт CPU. В JDK — `LockSupport.parkNanos`, spin-then-park внутри j.u.c.

```java
long deadline = System.nanoTime() + timeoutNanos;
while (!condition.getAsBoolean()) {
    if (System.nanoTime() >= deadline) break;
    Thread.onSpinWait(); // Java 9+: намёк CPU на spin-loop
}
```

---

## 10. Прерывание и остановка потока

`Thread.stop` / `suspend` / `resume` — deprecated: ломают инварианты и оставляют lock'и
в плохом состоянии.

Корректная модель — **cooperative interruption**:

```java
public void run() {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            workChunk();
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить флаг
            break;                              // и выйти
        }
    }
    releaseResources();
}

// снаружи:
worker.interrupt();
```

Нюансы флага:

```java
Thread.currentThread().interrupt();
Thread.interrupted();                 // true, флаг сброшен
Thread.interrupted();                 // false

Thread.currentThread().interrupt();
Thread.currentThread().isInterrupted(); // true
Thread.currentThread().isInterrupted(); // true — не сбрасывает
```

Блокирующий классический I/O `InputStream.read` не всегда реагирует на interrupt;
сетевой код — NIO / закрытие канала.

Демо: [InterruptHandlingDemo.java](code/InterruptHandlingDemo.java).

---

## 11. Пулы потоков и Executor

Создавать OS-поток дорого; unbounded `new Thread` на каждый запрос — путь к OOME и
деградации. Пул ограничивает параллелизм и переиспользует workers.

Иерархия: `Executor` → `ExecutorService` → `ThreadPoolExecutor` /
`ScheduledThreadPoolExecutor` / `ForkJoinPool`.

Типичные фабрики:

| Фабрика | Поведение |
|---------|-----------|
| `newFixedThreadPool(n)` | n потоков, unbounded `LinkedBlockingQueue` |
| `newCachedThreadPool` | кэш, растёт при нагрузке, idle ~60s умирают |
| `newSingleThreadExecutor` | один worker, строгая последовательность |
| `newScheduledThreadPool` | delayed / periodic |
| `newWorkStealingPool` | на базе ForkJoinPool |

Прод-конфигурация явным `ThreadPoolExecutor`:

```java
AtomicInteger seq = new AtomicInteger();
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        4,                              // core
        8,                              // max
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(100),  // bounded queue
        r -> {
            Thread t = new Thread(r, "api-worker-" + seq.incrementAndGet());
            t.setDaemon(false);
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy() // backpressure на caller
);

pool.execute(() -> handle(request));          // void
Future<Result> f = pool.submit(this::call);   // Future + исключения через get()

pool.shutdown();
if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
    pool.shutdownNow();
}
```

Размер пула (грубые эвристики):

- CPU-bound ≈ `N_cpu` или `N_cpu + 1`;
- I/O-bound ≈ `N_cpu * (1 + WT/ST)`;
- всегда ограничивать очередь и считать backpressure.

```java
pool.execute(task); // очередь полна + max threads заняты → RejectedExecutionException
                    // (при AbortPolicy)
```

Демо: [ThreadPoolRejectionDemo.java](code/ThreadPoolRejectionDemo.java).

На Java 21 для огромного числа блокирующих задач часто смотрят
`newVirtualThreadPerTaskExecutor()` вместо гигантского fixed pool.

---

## 12. Stack, heap, ThreadLocal, обмен данными

- **Stack** потока: frames, локальные переменные, операнды. Чужой поток стек не читает.
- **Heap**: объекты; shared между потоками → нужна безопасная публикация / синхронизация.
- Размер стека platform-потока: `-Xss` / `-XX:ThreadStackSize`.

```java
void method(int local) {          // local — в стеке текущего потока
    Holder h = new Holder(local); // объект Holder — в heap, ссылка h — в стеке
    sharedRef = h;                // публикация ссылки в shared → нужны правила JMM
}
```

Диагностика:

- thread dump: `jstack <pid>`, `kill -3`, JCmd `Thread.print`;
- heap dump: `jmap`, `-XX:+HeapDumpOnOutOfMemoryError`, JCmd `GC.heap_dump`.

`ThreadLocal` — per-thread значение; в пулах обязателен `remove()`:

```java
private static final ThreadLocal<String> REQUEST_ID =
        ThreadLocal.withInitial(() -> "none");

void handle(Request req) {
    REQUEST_ID.set(req.id());
    try {
        businessLogic(); // где угодно в этом потоке: REQUEST_ID.get()
    } finally {
        REQUEST_ID.remove(); // иначе «грязь» / leak в thread pool
    }
}
```

Обмен данными через очередь / exchanger:

```java
BlockingQueue<Job> queue = new LinkedBlockingQueue<>(256);
queue.put(job);          // producer
Job next = queue.take(); // consumer

Exchanger<Payload> exchanger = new Exchanger<>();
// поток A:
Payload theirs = exchanger.exchange(mine);
// поток B:
Payload fromA = exchanger.exchange(mineB);
```

Демо: [ThreadLocalIsolationDemo.java](code/ThreadLocalIsolationDemo.java).

---

## 13. Lock, ReadWriteLock, Semaphore

**`Lock`** (`java.util.concurrent.locks.Lock`) — явный замок: «перед критической секцией
взял, после — отпустил». Тот же смысл, что у `synchronized`, но с более гибким API.

Простая картинка:

```text
lock.lock();
try {
    // только один поток здесь
} finally {
    lock.unlock(); // обязательно, иначе замок «залипнет»
}
```

Самая частая реализация — `ReentrantLock` (можно войти повторно тому же потоку).

`synchronized` vs `ReentrantLock`:

| | `synchronized` | `ReentrantLock` |
|--|----------------|-----------------|
| Снятие lock | автоматически | обязательно в `finally` |
| Interruptible acquire | нет | `lockInterruptibly` |
| Timeout | нет | `tryLock(timeout)` |
| Условие | один wait-set | несколько `Condition` |
| Fairness | нет | опционально fair |
| Структура | блок/метод | явный API |

```java
ReentrantLock lock = new ReentrantLock();
Condition notEmpty = lock.newCondition();
Condition notFull = lock.newCondition();

lock.lockInterruptibly();
try {
    while (buffer.isEmpty()) {
        notEmpty.await();
    }
    Item item = buffer.remove();
    notFull.signal();
    return item;
} finally {
    lock.unlock();
}
```

Полный пример: [ReentrantLockBufferDemo.java](code/ReentrantLockBufferDemo.java).

Исторические обещания «ReentrantLock всегда быстрее под contention» устарели: intrinsic
locks в HotSpot сильно эволюционировали (biased locking убран в Java 15). Выбирают по
**возможностям API**, не по мифу о скорости.

`ReadWriteLock`:

```java
ReadWriteLock rw = new ReentrantReadWriteLock();
Lock readLock = rw.readLock();
Lock writeLock = rw.writeLock();

readLock.lock();
try {
    return cache.get(key); // много readers одновременно
} finally {
    readLock.unlock();
}

writeLock.lock();
try {
    cache.put(key, value); // эксклюзивная запись
} finally {
    writeLock.unlock();
}
```

`Semaphore` для ограничения параллелизма к scarce resource:

```java
Semaphore dbPermits = new Semaphore(10); // max 10 одновременных соединений

dbPermits.acquire();
try {
    return dataSource.getConnection();
} finally {
    dbPermits.release();
}
```

---

## 14. Синхронизаторы и Fork/Join

| Класс | Идея | Переиспользование |
|-------|------|-------------------|
| `CountDownLatch` | ждать, пока счётчик дойдёт до 0 | одноразовый |
| `CyclicBarrier` | N потоков встречаются на барьере | можно `reset` / циклически |
| `Phaser` | гибкий barrier/latch с фазами | динамическое число parties |
| `Exchanger` | парный обмен данными | — |
| `Semaphore` | permits | да |

```java
CountDownLatch done = new CountDownLatch(workers);
for (int i = 0; i < workers; i++) {
    pool.execute(() -> {
        try {
            doWork();
        } finally {
            done.countDown();
        }
    });
}
done.await(); // главный поток ждёт всех

CyclicBarrier barrier = new CyclicBarrier(4, () -> mergePartialResults());
// каждый worker:
computePart();
barrier.await(); // все четверо встретились → merge → можно следующий раунд
```

Демо: [LatchBarrierDemo.java](code/LatchBarrierDemo.java).

Fork/Join — divide-and-conquer + work-stealing:

```java
class SumTask extends RecursiveTask<Long> {
    private static final int THRESHOLD = 10_000;
    private final long[] data;
    private final int from, to;

    SumTask(long[] data, int from, int to) {
        this.data = data; this.from = from; this.to = to;
    }

    @Override
    protected Long compute() {
        if (to - from <= THRESHOLD) {
            long sum = 0;
            for (int i = from; i < to; i++) sum += data[i];
            return sum;
        }
        int mid = (from + to) >>> 1;
        SumTask left = new SumTask(data, from, mid);
        SumTask right = new SumTask(data, mid, to);
        left.fork();                 // асинхронно в пул
        Long rightResult = right.compute(); // текущий поток считает правую
        Long leftResult = left.join();
        return leftResult + rightResult;
    }
}

long sum = new ForkJoinPool().invoke(new SumTask(data, 0, data.length));
// parallelStream() внутри тоже опирается на common ForkJoinPool
```

Для блокирующего I/O Fork/Join обычно не тот инструмент (до virtual threads /
`ManagedBlocker`).

---

## 15. Singleton, immutability, практика

Потокобезопасные варианты:

```java
// 1) enum — простой и надёжный
enum Singleton { INSTANCE; }

// 2) eager static final
class Eager {
    static final Eager INSTANCE = new Eager();
    private Eager() {}
}

// 3) holder idiom — lazy + safe publication через init класса
class HolderSingleton {
    private HolderSingleton() {}
    private static final class Holder {
        static final HolderSingleton INSTANCE = new HolderSingleton();
    }
    static HolderSingleton get() { return Holder.INSTANCE; }
}

// 4) synchronized accessor
class SyncSingleton {
    private static SyncSingleton instance;
    static synchronized SyncSingleton get() {
        if (instance == null) instance = new SyncSingleton();
        return instance;
    }
}

// 5) DCL + volatile
class DclSingleton {
    private static volatile DclSingleton instance;
    static DclSingleton get() {
        DclSingleton local = instance;
        if (local == null) {
            synchronized (DclSingleton.class) {
                local = instance;
                if (local == null) {
                    instance = local = new DclSingleton();
                }
            }
        }
        return local;
    }
}
```

Демо: [DoubleCheckedLockingDemo.java](code/DoubleCheckedLockingDemo.java).

Immutability:

```java
record Money(BigDecimal amount, Currency currency) {
    Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("scale");
        }
    }
    Money plus(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException();
        return new Money(amount.add(other.amount), currency);
    }
}
// после safe publication Money можно читать из любого потока без sync
```

Принципы на практике:

```java
// именовать потоки
new ThreadFactoryBuilder().setNameFormat("payments-%d").build(); // или свой ThreadFactory

// узкая critical section
synchronized (lock) {
    state = state.update(delta); // только мутация
}
persist(state); // I/O вне lock
```

- предпочитать j.u.c синхронизаторы самописным `wait`/`notify`;
- concurrent-коллекции вместо `Collections.synchronized*`, когда нужна масштабируемость;
- не глотать `InterruptedException`;
- всегда думать про rejection / backpressure у пулов;
- меньше shared mutable state.

---

## 16. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [VolatileVisibilityDemo.java](code/VolatileVisibilityDemo.java) | visibility: plain vs `volatile` флаг |
| [RaceConditionAtomicDemo.java](code/RaceConditionAtomicDemo.java) | `volatile++` vs `AtomicInteger` |
| [WaitNotifyBoundedBufferDemo.java](code/WaitNotifyBoundedBufferDemo.java) | monitor, `wait` в цикле, bounded buffer |
| [ReentrantLockBufferDemo.java](code/ReentrantLockBufferDemo.java) | тот же буфер на `Lock` + 2 `Condition` |
| [DeadlockDemo.java](code/DeadlockDemo.java) | циклический захват двух мониторов |
| [InterruptHandlingDemo.java](code/InterruptHandlingDemo.java) | `interrupted` vs `isInterrupted`, restore flag |
| [StartVsRunDemo.java](code/StartVsRunDemo.java) | `start()` vs прямой `run()` |
| [DoubleCheckedLockingDemo.java](code/DoubleCheckedLockingDemo.java) | DCL + holder idiom |
| [ThreadPoolRejectionDemo.java](code/ThreadPoolRejectionDemo.java) | полная очередь → `RejectedExecutionException` |
| [LatchBarrierDemo.java](code/LatchBarrierDemo.java) | `CountDownLatch` и `CyclicBarrier` |
| [CasStackDemo.java](code/CasStackDemo.java) | lock-free stack на CAS |
| [ThreadLocalIsolationDemo.java](code/ThreadLocalIsolationDemo.java) | изоляция значений и `remove()` |

---

## 17. Вопросы для собеседования и ответы

### 1. Чем happens-before отличается от «просто volatile на всём»?

HB — частичный порядок между конкретными действиями. `volatile` даёт HB только между
записью и последующим чтением **того же** поля (плюс запреты на определённые reorder'ы).
Если потоки не образуют цепочку HB на нужных переменных, «навесить volatile везде»
не превращает программу в последовательно согласованную и не делает сложные инварианты
корректными. Нужна схема публикации: какие записи должны стать видимы перед каким чтением.

Пример достаточной схемы — «данные + ready» из [раздела 1](#11-happens-before).

### 2. Почему `count++` с `volatile int count` всё ещё гонка?

`count++` — read-modify-write: прочитать, прибавить, записать. `volatile` синхронизирует
отдельные read/write, но не делает тройку атомарной. Два потока читают одно значение и оба
пишут `+1` — потеря обновления. Нужны `AtomicInteger`, `synchronized` или `LongAdder`
(для статистики). Демо: [RaceConditionAtomicDemo.java](code/RaceConditionAtomicDemo.java).

### 3. Когда выбрать `notifyAll`, а когда `notify`?

`notify` будит одного. Если на одном мониторе ждут разные условия (например producers и
consumers), можно разбудить «не того» — он снова уснёт, а нужный продолжит ждать
(lost wakeup по сути). `notifyAll` дороже, но безопаснее. С `ReentrantLock` лучше
развести условия на разные `Condition` и делать `signal` точечно — см.
[ReentrantLockBufferDemo.java](code/ReentrantLockBufferDemo.java).

### 4. Чем `BLOCKED` отличается от `WAITING`?

`BLOCKED` — поток хочет войти в `synchronized`, монитор занят другим. `WAITING` /
`TIMED_WAITING` — поток сам вызвал ожидание (`wait`, `join`, `park`, `sleep` …) и
обычно уже не держит тот монитор, на котором вызвал `wait`. В thread dump это разные
стеки и разные причины «простоя».

### 5. Почему double-checked locking требует `volatile`?

Присвоение `instance = new Singleton()` — не атомарная публикация полностью
сконструированного объекта с точки зрения другого потока без HB. Без `volatile` читатель
может увидеть ненулевую ссылку и читать поля, инициализация которых ещё не видима.
`volatile` на `instance` даёт HB между записью ссылки и чтением. Альтернативы: holder,
`enum`, eager `static final`. Демо: [DoubleCheckedLockingDemo.java](code/DoubleCheckedLockingDemo.java).

### 6. Как правильно обработать `InterruptedException` в библиотечном коде?

Не глотать пустым `catch`. Типичные политики: пробросить checked дальше; обернуть в
unchecked, **восстановив** флаг (`Thread.currentThread().interrupt()`); завершить задачу
и очистить ресурсы. Выбор зависит от слоя: framework-код чаще восстанавливает флаг и
выходит, application-код может осмысленно завершать use-case.

### 7. Почему `Executors.newFixedThreadPool(n)` опасен на проде без оговорок?

Очередь unbounded (`LinkedBlockingQueue`). При медленных consumers задачи копятся → рост
heap → риск OOME, пока rejection ещё не случился. На проде задают bounded queue,
`CallerRunsPolicy` или свой handler, осмысленные имена потоков, `shutdown` с таймаутом —
как в примере `ThreadPoolExecutor` в [разделе 11](#11-пулы-потоков-и-executor).

### 8. Чем `CountDownLatch` отличается от `CyclicBarrier`?

Latch — «подождать события N раз», одноразовый, счётчик только вниз, ждущие и
«отсчитывающие» роли разделены. Barrier — «N участников должны встретиться», после
встречи можно использовать снова; есть опциональный barrier action. Для фаз алгоритма
часто удобнее `Phaser`. Демо: [LatchBarrierDemo.java](code/LatchBarrierDemo.java).

### 9. Virtual threads отменяют thread pools?

Не отменяют, меняют экономику. Для блокирующего I/O можно плодить много virtual threads
(часто one-task-one-thread) вместо пула platform threads на тысячи задач. Пулы и
семафоры всё ещё нужны, чтобы ограничить доступ к scarce resources (connections, CPU-heavy
участки). CPU-bound по-прежнему ограничивают число workers ≈ числу ядер.

### 10. `synchronized` или `ReentrantLock` — что сказать на собесе?

По умолчанию `synchronized` проще и безопаснее (нет забытого `unlock`). `ReentrantLock` —
когда нужны interruptible lock, timeout, fair lock, несколько `Condition`, tryLock для
избежания deadlock. Производительность сравнивать замером на своей нагрузке, не лозунгами
2010 года.

### 11. Как сделать потокобезопасный Singleton «правильно» сегодня?

Чаще всего `enum` или holder / `static final`. DCL оставляют, когда важна ленивость и
нельзя/неудобно enum (редко). Главное — не изобретать «ленивый singleton» без понимания
safe publication. Код вариантов — в [разделе 15](#15-singleton-immutability-практика).

### 12. Что не так с «неблокирующим ArrayList» через `volatile Object[]` и копирование?

Одиночная запись ссылки на новый массив может быть атомарной, но **составные** операции
`add`/`remove` без CAS/lock гоняются между собой:

```java
// оба потока читают один snapshot и публикуют свои копии → last-write-wins
Object[] snapshot = content;
Object[] next = Arrays.copyOf(snapshot, snapshot.length + 1);
next[snapshot.length] = item;
content = next; // без CAS потеряется чужой add
```

Настоящие lock-free структуры строят на CAS корня / узлов, либо берут
`CopyOnWriteArrayList` с понятным контрактом.

### 13. Как диагностировать deadlock в production?

Thread dump (`jstack`, JCmd `Thread.print`): искать `Found one Java-level deadlock` или
вручную циклы `waiting to lock ...` / `locked ...`. Митигация: порядок lock'ов, таймауты,
меньше вложенных блокировок. Демо локально: [DeadlockDemo.java](code/DeadlockDemo.java).
