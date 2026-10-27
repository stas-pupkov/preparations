# Микросервисы: паттерны, DDD, Saga, CQRS, 2PC

Java 17+. Заметка для собеседований. Каталог идей:
[microservices.io/patterns](https://microservices.io/patterns/index.html).

Связанные темы: события и outbox — [kafka.md](../kafka/kafka.md); транзакции БД —
[databases.md](../databases/databases.md).

## Содержание

1. [Когда микросервисы уместны](#1-когда-микросервисы-уместны)
2. [Карта паттернов microservices.io](#2-карта-паттернов-microservicesio)
3. [DDD и границы сервисов](#3-ddd-и-границы-сервисов)
4. [События в системе](#4-события-в-системе)
5. [CQRS](#5-cqrs)
6. [Saga](#6-saga)
7. [Двухфазный коммит (2PC)](#7-двухфазный-коммит-2pc)
8. [SLA, SLO, SLI](#8-sla-slo-sli)
9. [Демо-код](#9-демо-код)
10. [Вопросы для собеседования и ответы](#10-вопросы-для-собеседования-и-ответы)

---

## 1. Когда микросервисы уместны

Микросервисная архитектура (microservice architecture) — система из **независимо
деплоящихся** сервисов с своими границами данных и командами.

Имеет смысл, когда:

- разные части системы масштабируются и меняются с разной скоростью;
- команды могут жить относительно автономно;
- цена сетевых границ и eventual consistency приемлема.

Антипаттерн: «нарезали по классам Controller», но одна БД на всех и общий деплой —
получили распределённый монолит.

---

## 2. Карта паттернов microservices.io

Не чеклист «внедрить всё», а язык типовых проблем.

### Границы

| Паттерн | Идея |
|---------|------|
| Decompose by business capability | сервис ≈ возможность бизнеса (оплата, доставка) |
| Decompose by subdomain | сервис ≈ bounded context из DDD |
| Database per service | у сервиса своя БД; чужие таблицы не трогаем |
| Shared database | общая БД — проще старт, хуже автономия |

### Согласованность между сервисами

| Паттерн | Идея |
|---------|------|
| **Saga** | длинная бизнес-операция = цепочка локальных транзакций + компенсации |
| **2PC / XA** | atomic commit между resource managers; в MSA обычно избегают |
| **CQRS** | отдельно модель записи и модель чтения |
| API Composition | запрос склеивает ответы нескольких сервисов |
| Domain event | «что-то случилось» публикуем наружу |
| Event sourcing | состояние = журнал событий (тяжёлая дисциплина) |

### Надёжная отправка сообщений

| Паттерн | Идея |
|---------|------|
| Transactional outbox | в одной tx с БД пишем «письмо» в outbox-таблицу |
| Transaction log tailing | читаем WAL/binlog и публикуем изменения |
| Polling publisher | периодически читаем outbox и шлём в брокер |

### Связь и край

| Паттерн | Идея |
|---------|------|
| Messaging vs RPI | асинхронные сообщения vs синхронный HTTP/gRPC |
| Idempotent Consumer | повтор сообщения не ломает систему |
| API Gateway / BFF | единая точка входа / отдельный фасад под UI |
| Circuit Breaker | при падении зависимости быстро отказывать, а не висеть |
| Service discovery | как найти инстанс (client-side / server-side) |

### Наблюдаемость

Health check, metrics, log aggregation, distributed tracing — без них отладка
распределённой системы почти невозможна.

---

## 3. DDD и границы сервисов

**Domain-Driven Design (DDD)** — подход к моделированию **сложной предметной области**.
Это **не** «микросервисная архитектура» и не «мало сервисов в корпорации».

### Что важно на практике

| Понятие | Простыми словами |
|---------|------------------|
| Ubiquitous language | один словарь с бизнесом: «заказ», «резерв», не `OrderDTO2` |
| Bounded context | граница модели: в «Платежах» и «Складе» слово «товар» может значить разное |
| Aggregate | кластер сущностей с инвариантами; снаружи меняем через корень |
| Domain event | факт внутри домена: `OrderPaid` |

Микросервисы **часто** режут по bounded context — это стык DDD и MSA. Но DDD отлично
живёт и в модульном монолите.

Плохой ответ на собесе: «DDD = микросервисы на сущностях». Хороший: «DDD помогает
найти границы и язык; деплой-единицы выбираем отдельно».

---

## 4. События в системе

Три разных «события», которые часто смешивают:

### 1) Событие внутри одного приложения (in-process)

Spring: `ApplicationEventPublisher` + `@EventListener`.

```java
publisher.publishEvent(new OrderPaidEvent(orderId));

@EventListener
void onPaid(OrderPaidEvent e) {
    mail.sendReceipt(e.orderId());
}
```

Нюанс: если слушатель должен увидеть уже закоммиченные данные — бери
`@TransactionalEventListener(phase = AFTER_COMMIT)`. Иначе письмо уйдёт, а заказ
откатится.

### 2) Domain event

Часть модели: «оплата прошла». Может остаться внутри сервиса или стать сигналом
наружу.

### 3) Integration event

Контракт между сервисами (Kafka/Rabbit): версия, идемпотентный consumer, схема.

Правило: **сначала commit в своей БД, потом (надёжно) публикация** — обычно через
outbox, а не «HTTP в другом сервисе внутри той же tx».

Демо упрощённой цепочки: [DomainEventFlowDemo.java](code/DomainEventFlowDemo.java).

---

## 5. CQRS

**CQRS (Command Query Responsibility Segregation)** — разделяем:

- **команды** (изменить состояние): `PlaceOrder`, `CancelOrder`;
- **запросы** (прочитать): «список заказов пользователя с статусом доставки».

Зачем: модель записи удобна для инвариантов; модель чтения — для экранов и отчётов
(часто другая БД, денормализованная таблица, Elasticsearch).

```text
[API] --PlaceOrder--> [Write service + Write DB]
                          |
                     event OrderPlaced
                          v
                    [Projector] --> [Read DB / search]
                          ^
[API] --GetOrderView------------------+
```

CQRS **не требует** Event Sourcing. Event Sourcing — отдельное решение «хранить только
события».

Цена: eventual consistency (на экране статус «догоняет»), два пути данных, сложнее ops.

Когда стоит: тяжёлые разные read/write, сложные проекции. Когда нет: простой CRUD —
лишняя сложность.

Демо: [CqrsProjectionDemo.java](code/CqrsProjectionDemo.java).

---

## 6. Saga

**Saga** — способ провести **длинную бизнес-операцию через несколько сервисов** без
распределённой ACID-транзакции.

Идея: каждый сервис делает **свою** локальную транзакцию. Если следующий шаг упал —
выполняем **компенсации** (отменить резерв, вернуть деньги), а не «откатить всё одной
кнопкой 2PC» (см. [§7](#7-двухфазный-коммит-2pc)).

### Пример: заказ

1. `Order` создаёт заказ (`PENDING`).
2. `Warehouse` резервирует товар.
3. `Payment` списывает деньги.
4. `Order` переводит в `CONFIRMED`.

Если оплата не прошла → компенсация: снять резерв, отменить заказ.

### Два стиля

| | Choreography | Orchestration |
|--|--------------|---------------|
| Кто ведёт | сервисы слушают события друг друга | отдельный координатор (orchestrator) |
| Плюс | меньше центральной точки | проще видеть весь процесс |
| Минус | сложнее понять цепочку | оркестратор знает много о шагах |

```text
Choreography:
  OrderCreated → Warehouse reserved → PaymentTaken → OrderConfirmed
                 (если PaymentFailed → ReleaseStock, CancelOrder)

Orchestration:
  Saga → ReserveStock → ChargeCard → ConfirmOrder
       ↘ при ошибке: ReleaseStock / Refund
```

**Axon Framework** — один из Java-инструментов с CQRS/Event Sourcing и сагами. Saga ≠ Axon:
сагу можно писать руками на Spring + Kafka.

Демо: [SagaCompensationDemo.java](code/SagaCompensationDemo.java).

---

## 7. Двухфазный коммит (2PC)

**Two-Phase Commit** — протокол **atomic commit** между несколькими resource managers
(RM): либо все делают commit, либо все — abort.

Это **не** «полный распределённый ACID». Изоляция и consistency остаются локальными
свойствами каждой БД; 2PC согласует только **исход** транзакции.

В Java-стеке обычно: **JTA + XA** (`XAResource`, `XADataSource`), TM — Narayana /
Atomikos / контейнер app server. В PostgreSQL видны команды `PREPARE TRANSACTION`,
`COMMIT PREPARED` / `ROLLBACK PREPARED` и view `pg_prepared_xacts`.

### Роли

| Роль | Кто | Что делает |
|------|-----|------------|
| Coordinator / TM | Transaction Manager | ведёт протокол, durably пишет решение |
| Participant / RM | БД, JMS broker, … | prepare / commit / rollback по XA |

### Протокол (happy path)

```text
App / TM
  │  begin, enlist RM1, RM2, … ; работа на каждом RM
  │
  ├─ Phase 1: PREPARE
  │     TM → всех: prepare(xid)
  │     RM: flush в durable storage, держит locks,
  │         отвечает YES (prepared) или NO
  │     YES = «потом выполню commit или rollback по решению TM»
  │
  ├─ TM durably пишет решение: COMMIT или ABORT
  │
  └─ Phase 2: COMMIT / ROLLBACK
        TM → всех: commit(xid) или rollback(xid)
```

Инварианты:

1. После **YES** участник **не может** unilaterally отказаться — обязан уметь и commit,
   и rollback по команде TM.
2. Решение TM после записи в его log — **финальное**; после рестарта TM добивает phase 2
   из log.
3. Пока RM в **prepared / in-doubt**, он держит **locks** (часто и undo) — иначе нельзя
   гарантировать возможность commit/rollback.

### Blocking и in-doubt

Классическая дыра: TM падает после того, как **все** RM ответили YES, но **до**
(или во время) рассылки commit/abort.

Участники не знают решения и **не могут** безопасно выбрать исход сами (риск
рассинхрона) → **блокируются** до восстановления TM или ручного вмешательства.
Это **in-doubt transaction**: данные заблокированы; рестарт БД locks prepared-tx
**не снимает** (иначе ломается atomicity).

Практика:

- нормальный путь — recovery TM + `xa_recover` у RM;
- катастрофа (потерян log TM) — **heuristic** commit/rollback (`COMMIT FORCE` и аналоги).
  Heuristic = сознательный риск нарушить atomicity ради разблокировки.

Проще: кто-то ответил NO / timeout на prepare → TM шлёт abort всем, кто уже YES.

### Почему плохо стыкуется с микросервисами

| Фактор | Эффект |
|--------|--------|
| Долгие locks на prepare | latency × contention |
| TM как SPOF / bottleneck | availability всей «глобальной» tx |
| Нужен XA у каждого RM | HTTP-сервис «без XA» в 2PC честно не влезает |
| Partial failure — норма MSA | как раз сценарий blocking / in-doubt |
| Ops | мониторинг prepared, heuristic, инциденты |

«Распределённая tx через REST» без XA — **не 2PC**, а самодельный протокол без гарантий.

Где 2PC ещё уместен: несколько XA-ресурсов **внутри одного** runtime/trust domain
(БД + JMS в монолите). Между независимыми сервисами с своими БД — обычно **Saga** +
outbox / идемпотентность.

### 2PC vs Saga

| | 2PC | Saga |
|--|-----|------|
| Consistency | atomic commit исхода | обычно eventual; есть промежуточные состояния |
| Isolation между сервисами | пока prepared — locks | шаги уже закоммичены; эффекты видны снаружи |
| «Откат» | rollback по протоколу | **компенсация** (не всегда обратная операция 1:1) |
| Failure mode | blocking / in-doubt | дожим шагов и компенсаций, идемпотентность |

Saga ≠ «мягкий 2PC»: другая модель консистентности. Между шагами клиент может увидеть
«заказ есть, оплаты ещё нет» — осознанный trade-off.

**3PC** пытается снизить blocking при падении одного процесса; на практике в БД/Java
почти не используют. Настоящий non-blocking commit упирается в consensus (Paxos Commit
и аналоги) — другой класс систем, не «поставь 3PC вместо 2PC в MSA».

---

## 8. SLA, SLO, SLI

Простыми словами — «о чём договорились» и «как измеряем».

| Термин | Смысл | Пример |
|--------|--------|--------|
| **SLI** (indicator) | что меряем | доля успешных HTTP 2xx/3xx, latency p99 |
| **SLO** (objective) | внутренняя цель | p99 < 200 ms, availability 99.9% |
| **SLA** (agreement) | договор с клиентом/бизнесом | если availability < 99.5% — штраф / кредит |

99.9% в месяце ≈ 43 минуты простоя. Поэтому «пять девяток» дорого.

На собесе: SLA без SLI — пустые слова; средний latency врёт — смотри percentiles.

---

## 9. Демо-код

| Файл | Что разбирает |
|------|----------------|
| [SagaCompensationDemo.java](code/SagaCompensationDemo.java) | шаги саги + компенсация при сбое оплаты |
| [CqrsProjectionDemo.java](code/CqrsProjectionDemo.java) | write model → событие → read model |
| [DomainEventFlowDemo.java](code/DomainEventFlowDemo.java) | commit, потом реакция на событие |

---

## 10. Вопросы для собеседования и ответы

### 1. DDD — это микросервисы?

Нет. DDD — про модель и границы предметной области. Микросервисы — про деплой и
автономию. Часто режут сервисы по bounded context, но это стык практик, не определение.

### 2. Чем Saga отличается от распределённой транзакции (2PC)?

2PC — atomic commit через TM + prepare у XA-участников; держит locks до решения,
блокируется при падении TM после YES → in-doubt. Хрупко при независимых сервисах и
сети.

Saga = локальные tx + компенсации, обычно eventual consistency. Нет волшебного отката
всего графа одной командой БД; между шагами видны промежуточные состояния.

### 3. Почему 2PC называют blocking? Что такое in-doubt и heuristic decision?

Все ответили YES, TM умер до рассылки решения — участники не знают commit/abort и не
могут unilaterally выбрать без риска рассинхрона → ждут. In-doubt: locks (часто и на
чтение «какой версии верить») до recovery. Heuristic force commit/rollback — аварийный
выход ценой возможного нарушения atomicity.

### 4. Даёт ли 2PC serializability между сервисами? Можно ли «2PC по HTTP»?

Нет автоматически: согласуется только исход commit/abort; изоляция локальна (+ locks на
время prepared). «2PC по HTTP» между Order и Payment без XA/prepare у RM — обычно не
2PC, а самоделка без тех же гарантий.

### 5. Когда брать CQRS?

Когда read и write сильно различаются по модели/нагрузке и готовы жить с задержкой
проекций. Для простого CRUD — оверхед.

### 6. Как надёжно опубликовать событие после записи в БД?

Transactional outbox (или CDC по outbox/WAL): в одной локальной tx пишем бизнес-данные
и запись «отправить событие»; отдельный процесс публикует в брокер. «Сначала Kafka, потом
БД» или «HTTP соседу внутри tx» — типичные дыры.

### 7. Что такое SLA на пальцах?

Договорённый уровень сервиса с последствиями. Рядом SLO (цель команды) и SLI (метрика).
Пример: availability 99.9% по успешным health/запросам за месяц.
