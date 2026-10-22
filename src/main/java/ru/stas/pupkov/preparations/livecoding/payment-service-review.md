# Проблемы в `PaymentService`

## Условие

```java
@Transactional(readOnly = true) // под вопросом
@RequiredArgsConstructor
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final CashService cashService;

    private CardService cardService;
    private QrService qrService;

    public List<PaymentDto> getPayments(Pageable pageable) { ... }

    public PaymentDto createPayment(PaymentDto dto) {
        var payment = paymentMapper.toEntity(dto);
        payment.setCreatedAt(new Date());
        publishPaymentCreationAsync(payment);
        paymentRepository.save(payment);
        return dto;
    }

    public PaymentDto confirmPayment(ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId()).get();
        payment.isConfirmed(true);
        payment.updatedAt(new Date());
        return paymentMapper.toDto(payment);
    }

    @Async
    public void publishPaymentCreationAsync(Payment payment) {
        if (payment.getType().equals(PaymentType.CASH)) {
            cashService.notifyCreation(payment);
        } else if (payment.getType().equals(PaymentType.CARD)) {
            cardService.notifyCreation(payment);
        } else if (payment.getType().equals(PaymentType.QR)) {
            qrService.notifyCreation(payment);
        }
    }
}
```

## Проблемы (по убыванию)

### 1. `@Transactional(readOnly = true)` на классе

Аннотация на классе действует на **все** public-методы (если нет override на методе).

- `createPayment` / `confirmPayment` — **запись**, а tx read-only.
- Hibernate может не flush’ить dirty state / оптимизировать сессию как read-only → confirm «как будто сохранился», а в БД нет.
- Комментарий «под вопросом» как раз про это: readOnly только на `getPayments`, на класс — ошибка.

Фикс: убрать с класса; `@Transactional(readOnly = true)` на read-методах; `@Transactional` на write.

### 2. `cardService` / `qrService` не в конструкторе

`@RequiredArgsConstructor` внедряет только **`final`** поля.  
`cardService`, `qrService` без `final` и без `@Autowired` → остаются **`null`** → NPE на CARD/QR.

### 3. `@Async` + self-invocation

`publishPaymentCreationAsync` вызывается как `this....` из того же бина → **прокси Spring не участвует** → `@Async` не работает, вызов синхронный в том же потоке.

Фикс: вынести publisher в другой бин / self-injection `@Lazy`.

### 4. Notify **до** `save` (+ до commit)

Даже при рабочем async:

- у `payment` может не быть id;
- tx ещё не закоммичена → другой сервис/поток не увидит платёж (или увидит фантом, если читает ту же tx — тут уже другой поток).

Правильнее: save → **afterCommit** → notify (или outbox).

### 5. `createPayment` возвращает входной `dto`

Не маппит сохранённую entity (id, audit-поля с БД). Клиент не получает актуальный id/даты.

### 6. `confirmPayment`

- `.get()` без проверки → `NoSuchElementException` вместо 404.
- `payment.isConfirmed(true)` / `payment.updatedAt(...)` — похоже на **сломанные сеттеры** (имена как у getter/fluent без `set`). Если это не мутаторы — состояние не меняется.
- Нет явного `save`; при dirty checking + **readOnly** запись сомнительна.
- Нет проверки идемпотентности / статуса «уже confirmed».

### 7. `if-else` по `PaymentType` (OCP)

Новый тип → правка сервиса. Strategy / Map\<Type, Notifier\> / список handlers.

### 8. Прочее

- `getType().equals(...)` — NPE, если type null; лучше `PaymentType.CASH.equals(...)`.
- `java.util.Date` → лучше `Instant` / `OffsetDateTime`.
- Нет `@EnableAsync` (вне класса) — async всё равно мёртв без него + без прокси.
- Нет обработки ошибок notify / ретраев; длинный «and even more» — code smell.

## Как чинить коротко (ответ на собесе)

1. Tx: readOnly только на GET; write — отдельный `@Transactional`.  
2. Все зависимости `final` + constructor.  
3. Async в другой компонент; вызов после commit.  
4. `save` → return `toDto(saved)`.  
5. confirm: `orElseThrow`, нормальные setters/`save`, валидный write-tx.  
6. Нотификации — стратегия по типу.

## Демо

Отдельный runnable не обязателен (типичные Spring-ловушки уже в `CacheablePostConstruct` / tx-заметках). Суть self-invocation `@Async` та же, что у `@Cacheable`/`@Transactional` через `this`.

## Вопросы для собеседования и ответы

**Почему readOnly на классе ломает confirm?**  
Сессия/транзакция помечены read-only; провайдер может не писать изменения в БД при flush/commit.

**Почему `@Async` «не асинхронный»?**  
Вызов мимо Spring-прокси (self-invocation), advice не применяется.

**Почему нельзя notify до save?**  
Нет гарантированного id/commit; получатель нотификации не найдёт платёж или получит неконсистентные данные.
