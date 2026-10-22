# Code review: `bookSeat` (`@Transactional` + клиенты + цена)

## Условие

```java
@Transactional
public void bookSeat(String seatCode, UUID ticketId) {
    // 1 бронь
    var ticket = ticketRepository.findById(ticketId);
    var seatBooking = new SeatBooking(seatCode, ticket.get().getFlightId(), ticketId, BookingStatus.BOOKED);
    seatBookingRepository.save(seatBooking);

    // 2 тарификация
    var basePrice = tariffClient.getBasePrice(ticket.get().getPlaneModel(), seatCode);
    Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    var userData = customerClient.getCustomer(userId);
    System.out.println("Найден пользователь " + userData.getFio() + " номер документа " + userData.getDocument());
    var price = basePrice;
    if (userData.getTariff() == "PREMIUM") {
        price = basePrice * 0.5d;
    }
    if (userData.getTariff() == "ULTRA") {
        price = basePrice * 0.8d;
    }

    // 3 выставление счёта
    var invoice = new Invoice(price, ticketId, userId);
    paymentClient.sendInvoice(invoice);
    System.out.println("Счет выставлен");
}
```

## Проблемы (по убыванию важности)

### 1. Внешние вызовы внутри `@Transactional`

В одной транзакции БД: `tariffClient`, `customerClient`, `paymentClient` (сеть).

- JDBC-connection / locks держатся на время HTTP → риск исчерпания пула, долгие tx.
- **Dual-write:** `sendInvoice` уже ушёл во внешнюю систему, а tx ещё может откатиться (ошибка после вызова, timeout commit) → счёт есть, брони в БД нет (или наоборот при другом порядке/ретраях).
- Атомарности «БД + payment» нет (см. задачу про rename файла).

**Как на собесе:** короткая tx только на бронь (или PENDING); invoice — `afterCommit` / outbox / отдельный шаг саги; идемпотентность платежного вызова.

### 2. Сравнение строк через `==`

```java
userData.getTariff() == "PREMIUM"
userData.getTariff() == "ULTRA"
```

Сравниваются ссылки, не содержимое. Строка с клиента/`new String` / из JSON почти никогда не та же ссылка, что литерал → условия **ложны**, скидки не применяются, клиент платит полную цену.

Нужно: `"PREMIUM".equals(userData.getTariff())` (null-safe) или `enum`.

### 3. Деньги через `double` (`* 0.5d`)

Бинарная плавающая точка непригодна для валют (ошибки округления, хвосты). Нужны `BigDecimal` (с политикой scale/`RoundingMode`) или minor units (`long` копейки).

### 4. `Optional.get()` без проверки

`ticketRepository.findById` → сразу `ticket.get()`. Нет билета → `NoSuchElementException`, а не доменная 404.  
`orElseThrow(() -> new NotFoundException(...))`, один раз сохранить entity в переменную.

### 5. Гонки и идемпотентность брони

Нет видимой уникальности `(flightId, seatCode)` / блокировки ряда. Два параллельных `bookSeat` на одно место → две BOOKED.  
Нужны: unique constraint + обработка конфликта, `SELECT FOR UPDATE` места/рейса, идемпотентный ключ по `ticketId`.

Статус сразу `BOOKED` до оплаты — спорно; часто `RESERVED`/`PENDING_PAYMENT`, confirm после оплаты.

### 6. SecurityContext: грубый cast principal

```java
(Long) ...getAuthentication().getPrincipal()
```

Часто principal — `UserDetails`, JWT `String` subject, не `Long`. NPE, если `getAuthentication()`/`getPrincipal()` null.  
Явный security facade / `@AuthenticationPrincipal`, проверка authenticaton.

### 7. PII в `System.out.println`

ФИО и номер документа в stdout — утечка в логи/агрегаторы, не compliance-friendly. SLF4J + маскирование/не логировать документ; уровень DEBUG только в non-prod при необходимости.

### 8. Нет устойчивости к сбоям клиентов

Нет timeout/retry/circuit breaker, нет явной обработки 4xx/5xx tariff/customer/payment. Частичный успех (бронь в persistence context + упавший invoice) при rollback всё же откатит БД, но при `afterCommit`-ошибках нужна компенсация — сейчас модели нет.

### 9. Прочие замечания

- Два независимых `if` по тарифу: при кривых данных оба могли бы сработать; логичнее `switch` / `else if` / стратегия скидки.
- Нет валидации `seatCode`, принадлежности ticket пользователю (IDOR: забронировать чужим `ticketId`).
- `ticket.get()` несколько раз — шум; после извлечения работать с entity.
- Смешение ответственности: бронь + ценообразование + биллинг в одном методе — сложно тестировать и эволюционировать (SRP).

## Как перестроить (скелет ответа)

1. Авторизация: ticket принадлежит user; seat свободен.  
2. Короткая tx: создать бронь `PENDING` + unique seat.  
3. Посчитать цену **вне** длинной tx (или читать тариф из своего кэша/БД), `BigDecimal` + enum тарифа.  
4. После commit — outbox/event → `sendInvoice` идемпотентно; на успех — `BOOKED` / на оплату.  
5. Логи без PII; метрики; явные доменные ошибки.

## Демо

- [BookSeatPitfallsDemo.java](code/BookSeatPitfallsDemo.java) — `==` vs `equals` для тарифа и `double` vs `BigDecimal` на скидке.

## Вопросы для собеседования и ответы

**Почему invoice внутри tx опаснее, чем «просто медленно»?**  
Медленность — пул и locks. Опаснее рассинхрон с внешней системой: откат БД не отменяет уже принятый счёт.

**Почему скидка «не работает» при живом PREMIUM с API?**  
`==` на String. В JSON-десериализации новые экземпляры строк → условие false.

**BOOKED до оплаты — ок?**  
Зависит от домена; обычно место держат коротко как hold, финальный BOOKED — после денег/таймаута hold. Иначе overbooking или «забронировано, но не оплачено» без политики.
