# Удалятся ли `A` и `B` при циклических ссылках?

## Условие

```java
A objA = new A();
B objB = new B();

objA.b = objB;
objB.a = objA;

objA = null;
objB = null;

// Удалятся ли объекты A и B?
System.gc();
```

(У `A` есть поле `B b`, у `B` — поле `A a`.)

## Ответ

**Да, они становятся unreachable и могут быть собраны GC.** Цикл `A ↔ B` сам по себе сборке **не мешает**.

`System.gc()` — только **подсказка** JVM: немедленная и гарантированная сборка **не** обещается. Вопрос на собесе обычно про reachability, не про то, что `gc()` обязательно что-то удалит «прямо сейчас».

## Почему

Java GC (generational / mark-sweep и т.п.) смотрит **достижимость от GC roots** (стек, статики, JNI…), а не считает ссылки как reference counting.

После `objA = null` и `objB = null`:

- из корней до объектов пути нет;
- взаимные ссылки образуют «остров» мусора → оба объекта eligible for collection.

В языках с чистым refcounting цикл мог бы «утечь»; в Java — нет.

## Демо

- [GcCircularReferencesDemo.java](code/GcCircularReferencesDemo.java) — `WeakReference`: после `null` + `System.gc()` ссылки часто очищаются (не гарантия API, но иллюстрация).

## Вопросы для собеседования и ответы

**Чем это отличается от reference counting?**  
RC не собирает циклы без cycle detector. Tracing GC собирает любые unreachable-графы, в том числе с циклами.

**Гарантирует ли `System.gc()` удаление?**  
Нет (`Runtime.gc` / `System.gc` — hint). Для тестов иногда используют weak/phantom + очередь, всё равно без жёстких гарантий в спецификации.

**А если бы осталась статическая/`ThreadLocal` ссылка на `A`?**  
Тогда весь цикл reachable через `A` → оба живы.
