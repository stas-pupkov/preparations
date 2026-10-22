# Проблемы в `createOrder` (idempotency key)

## Условие

```java
@Transactional
public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {
    Optional<IdempotencyRecord> existing =
        idempotencyRepository.findByIdempotencyKey(idempotencyKey);

    if (existing.isPresent()) {
        return new OrderResponse(existing.get().getOrderId(), "DUPLICATE");
    }

    OrderEntity order = new OrderEntity();
    order.setUserId(request.userId());
    order.setProductId(request.productId());
    order.setQuantity(request.quantity());
    order = orderRepository.save(order);

    IdempotencyRecord record = new IdempotencyRecord();
    record.setIdempotencyKey(idempotencyKey);
    record.setStatus(IdempotencyStatus.SUCCESS);
    record.setOrderId(order.getId());
    idempotencyRepository.save(record);

    return new OrderResponse(order.getId(), "CREATED");
}
```

## Главная проблема: race condition (check-then-act)

Два параллельных запроса с одним `idempotencyKey`:

1. оба делают `find` → empty;
2. оба создают **разные** заказы;
3. оба пишут idempotency-record.

`@Transactional` **не** сериализует такие проверки между сессиями (READ COMMITTED). Без `UNIQUE(idempotency_key)` получите дубли заказов; с unique — один упадёт на insert record (или оба создадут order до падения второго).

Нужно: **unique constraint** + обработка `DataIntegrityViolation` / `DuplicateKey` → повторный `find` и ответ как DUPLICATE (или 409 с тем же order id).

Жёстче: `SELECT … FOR UPDATE` / advisory lock по ключу, или insert idempotency **сначала** в статусе `IN_PROGRESS` (unique), потом order, потом SUCCESS.

## Остальные проблемы

### 1. Нет unique / нет обработки конфликта

В коде не видно защиты на уровне схемы и catch на constraint violation. Без этого идемпотентность только «на бумаге».

### 2. Порядок «order → idempotency»

При падении после `save(order)` и до/на `save(record)`:

- rollback всей tx → ок, можно ретраить;
- если когда-нибудь разнесут по разным tx — order без ключа / ключ без order.

Надёжный паттерн: сначала занять ключ (`IN_PROGRESS`), потом бизнес, потом `SUCCESS` + `orderId`. При ретрае видеть `IN_PROGRESS` / таймаут.

### 3. Нет валидации ключа и request

Пустой/`null` key, слишком длинный key, отрицательный `quantity` — не видно. Идемпотентность с `null` ключом бессмысленна.

### 4. Семантика DUPLICATE vs тот же body

Классика Stripe-style: тот же key + **другой** payload → 422 conflict, не молча тот же order. Здесь payload не сравнивается с сохранённым запросом/хешем.

### 5. Только SUCCESS

Нет `FAILED` / хранения ответа ошибки: повтор после фейла может снова гонять создание вместо возврата зафиксированной ошибки.

### 6. Изоляция и производительность

Каждый create — read + 2 write. Под нагрузкой без unique+короткого пути на hit — лишние заказы и конфликты.

### 7. API-мелочи

- Магические строки `"DUPLICATE"` / `"CREATED"` → enum.
- `existing.get()` без сохранения в переменную — шум.
- Неясно, уникален ли key глобально или per-user (`(user_id, key)`).

## Как отвечать на собесе (фикс)

```text
1) UNIQUE(idempotency_key)  [или (user_id, key)]
2) try: insert IN_PROGRESS / или create order+record
3) on unique violation: re-read → return stored orderId + DUPLICATE
4) опционально: hash(request) для mismatch
5) одинаковый ответ на ретраи (включая тело)
```

В одной tx с unique на key этого часто достаточно для at-most-once order.

## Демо

- [IdempotentCreateOrderRaceDemo.java](code/IdempotentCreateOrderRaceDemo.java) — модель check-then-act без unique vs с unique+retry.

## Вопросы для собеседования и ответы

**Почему `@Transactional` не спасает от двух CREATED?**  
Изоляция не даёт mutual exclusion на «нет строки». Две tx обе видят отсутствие ключа.

**Где ставить unique — на orders или на idempotency?**  
На таблицу идемпотентности (ключ запроса). Order id порождается один раз и кладётся в record.

**IN_PROGRESS зачем?**  
Чтобы второй запрос не создавал второй order, пока первый ещё работает; нужна политика TTL/stuck recovery.
