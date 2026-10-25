# Микросервисная система бронирования отелей

## Запуск

Требования: JDK 21 либо только Docker.

С помощью docker compose:

```bash
docker compose up --build
```

Альтернативный вариант: локально - каждый сервис отдельно, начиная с Eureka:

```bash
./gradlew :eureka-server:bootRun
./gradlew :hotel-service:bootRun
./gradlew :booking-service:bootRun
./gradlew :api-gateway:bootRun
```

Переменные окружения:

| Переменная                          | По умолчанию                   | Назначение                                                                    |
|-------------------------------------|--------------------------------|-------------------------------------------------------------------------------|
| `JWT_SECRET`                        | dev-секрет                     | общий секрет, одинаковый во всех сервисах                                     |
| `DEMO_DATA`                         | `true`                         | предзаполнение демо-данными                                                   |
| `EUREKA_URL`                        | `http://localhost:8761/eureka` | адрес Eureka                                                                  |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | - (в Docker `ecs`)             | `ecs` - логи в JSON, `traceId` и `bookingId` - отдельные поля                 |
| `LOG_FORMAT`                        | `ecs`                          | только для `docker compose`: `LOG_FORMAT= docker compose up` - текстовые логи |

- Eureka dashboard: http://localhost:8761
- Swagger UI: http://localhost:8081/swagger-ui.html (Hotel), http://localhost:8082/swagger-ui.html (Booking);
  кнопка *Authorize* принимает токен из `/api/bookings/user/auth`.

Все запросы - через Gateway `http://localhost:8080`. Кроме регистрации и входа, нужен заголовок
`Authorization: Bearer <token>`. Пример: войти как `user` / `user123` (см. [демо-данные](#9-демо-данные)),
затем вызвать `GET /api/rooms/recommend?startDate=...&endDate=...` и `POST /api/bookings/booking`.

## 1. Планирование занятости номеров

- **Равномерная загрузка.** `GET /api/rooms/recommend` возвращает свободные на даты номера по возрастанию
  `times_booked`, при равенстве - по `id` (индекс `idx_rooms_times_booked (times_booked, id)`). Счётчик
  увеличивается при каждом успешном удержании слота, поэтому реже бронируемые номера предлагаются первыми.
- **Автоподбор.** `POST /api/bookings/booking` с `autoSelect: true` берёт номера из рекомендаций по порядку:
  если первый успели занять, пробуется следующий (не больше `booking.auto-select-max-rooms`).
- **Предотвращение простоя.** Номера, выведенные из эксплуатации (`available=false`), не попадают в выдачу;
  статистика загрузки (`GET /api/hotels/statistics`) показывает простаивающие номера и средний `times_booked`.
- **Параллельные бронирования.** `confirm-availability` выполняется под пессимистической блокировкой строки
  номера (`PESSIMISTIC_WRITE`): проверка пересечения дат и создание блокировки слота атомарны, двойного
  бронирования одного номера на пересекающиеся даты не бывает. `version` в сущностях - оптимистическая
  блокировка от конкурентных изменений (409).
- **Идемпотентность.** Клиент передаёт `requestId`: повтор возвращает то же бронирование, а не создаёт новое
  (`uk_bookings_request_id`); шаг саги в Hotel идемпотентен по своему `requestId`
  (`uk_room_reservations_request_id`). `requestId` чужого запроса -> 409.

## 2. Согласованность данных: сага

Подробности - [ADR-2](docs/adr/2-booking-saga.md).

1. `POST /api/bookings/booking` -> бронирование сохраняется в статусе `PENDING`.
2. Booking вызывает `POST /api/rooms/{id}/confirm-availability` (сервисный токен): Hotel под блокировкой
   строки номера проверяет пересечения дат и создаёт временную блокировку слота.
3. Успех -> `CONFIRMED`; номер занят -> `CANCELLED` (409), при `autoSelect` - пробуется следующий
   рекомендованный номер; тайм-аут/5xx после повторов -> компенсация `release` и `CANCELLED` (503).
4. Если сага прервалась и бронирование висит в `PENDING` дольше 5 минут, фоновая задача
   (`PendingBookingRecovery`) отменяет его (`PROCESSING_TIMEOUT`) и снимает блокировку в Hotel.
5. Отмена пользователем (`DELETE /api/bookings/booking/{id}`) -> `CANCELLED` (`USER_CANCELLED`) и `release`
   в Hotel - номер снова свободен.

Устойчивость вызовов Booking -> Hotel (`HttpHotelClient`, Resilience4j, `booking-service/.../application.yml`):

| Механизм        | Настройка                                                                   |
|-----------------|-----------------------------------------------------------------------------|
| Timeout         | connect 1 с, read 2 с                                                       |
| Retry           | 3 попытки, экспоненциальная пауза от 200 мс; только для тайм-аутов и 5xx    |
| Circuit Breaker | окно 10 вызовов, порог отказов 50 %, разомкнут 10 с -> сразу 503 без вызова |

Проверяется тестами `BookingServiceTest`, `HttpHotelClientTest`, `PendingBookingRecoveryTest`.

## 3. Booking Service

| Метод и путь                        | Роль  | Описание                                                                           |
|-------------------------------------|-------|------------------------------------------------------------------------------------|
| `POST /api/bookings/user/register`  | -     | регистрация (роль USER), возвращает токен                                          |
| `POST /api/bookings/user/auth`      | -     | вход, возвращает токен на 1 час                                                    |
| `POST /api/bookings/booking`        | USER  | бронирование: `roomId` или `autoSelect: true`; идемпотентно по `requestId`         |
| `GET /api/bookings`                 | USER  | история своих бронирований: `page`, `size`, `sort` (по умолчанию `createdAt,desc`) |
| `GET /api/bookings/booking/{id}`    | USER  | своё бронирование (чужое - 404)                                                    |
| `DELETE /api/bookings/booking/{id}` | USER  | отмена своего бронирования, освобождение номера                                    |
| `GET /api/bookings/user`            | ADMIN | список пользователей (пагинация)                                                   |
| `GET /api/bookings/user/{id}`       | ADMIN | пользователь                                                                       |
| `POST /api/bookings/user`           | ADMIN | создать пользователя с любой ролью                                                 |
| `PATCH /api/bookings/user`          | ADMIN | изменить переданные поля (`id` в теле)                                             |
| `DELETE /api/bookings/user?id={id}` | ADMIN | удалить пользователя без активных бронирований                                     |

- **Только свои бронирования**: выборка по `userId` из токена; чужое бронирование - 404, а не 403, чтобы не
  раскрывать его существование.
- **Идемпотентность** по `requestId` - см. [раздел 1](#1-планирование-занятости-номеров).
- **Пагинация и сортировка** - `page`, `size`, `sort`; неизвестное поле сортировки -> 400.
- **Валидация дат**: даты ISO (`2030-01-15`), `startDate` не в прошлом, `endDate` строго позже `startDate`;
  период - полуинтервал, `endDate` - день выезда.

## 4. Hotel Service

| Метод и путь                                      | Роль     | Описание                                                                                                 |
|---------------------------------------------------|----------|----------------------------------------------------------------------------------------------------------|
| `GET /api/hotels`                                 | USER     | отели: фильтры `city`, `name` (подстрока), `page`, `size`, `sort`                                        |
| `GET /api/hotels/{id}`                            | USER     | отель                                                                                                    |
| `POST /api/hotels`, `PUT/DELETE /api/hotels/{id}` | ADMIN    | CRUD отелей                                                                                              |
| `GET /api/rooms`                                  | USER     | свободные номера: `hotelId`, `startDate`, `endDate` (по id)                                              |
| `GET /api/rooms/recommend`                        | USER     | те же номера по возрастанию `times_booked`, затем id                                                     |
| `GET /api/rooms/{id}`                             | USER     | номер                                                                                                    |
| `POST /api/rooms`, `PUT/DELETE /api/rooms/{id}`   | ADMIN    | CRUD номеров; `available=false` выводит номер из эксплуатации                                            |
| `GET /api/rooms/statistics`                       | ADMIN    | загрузка номеров за `[startDate, endDate)`, `hotelId` - опционально                                      |
| `GET /api/hotels/statistics`                      | ADMIN    | загрузка отелей: доля занятых ночей номеров в эксплуатации, простаивающие номера, средний `times_booked` |
| `POST /api/rooms/{id}/confirm-availability`       | internal | шаг саги; через Gateway - 404                                                                            |
| `POST /api/rooms/{id}/release`                    | internal | компенсация; через Gateway - 404                                                                         |

- **Фильтры и сортировки**: отели - по городу и подстроке названия с пагинацией; номера - по отелю и датам.
- **Аналитика**: загрузка каждого номера и отеля за период, простаивающие номера, средний `times_booked`.

## 5. Gateway и Eureka

Подробности - [ADR-1](docs/adr/1-api-routing.md).

- Все сервисы регистрируются в Eureka (`eureka-server`, порт 8761); Gateway маршрутизирует через
  `lb://hotel-service` и `lb://booking-service` - адреса и экземпляры обнаруживаются динамически.
- Фильтры Gateway: проверка JWT на входе (401 без валидного токена, открыты только `register`, `auth`, health),
  внутренние маршруты `confirm-availability`/`release` закрыты (404), присвоение и проброс `X-Trace-Id`,
  access-лог с длительностью запроса.

Проверяется тестом `ApiGatewayApplicationTests`.

## 6. Безопасность

Подробности - [ADR-3](docs/adr/3-security-and-tracing.md).

- **JWT**: HS256 с общим секретом, проверка `iss` и `exp`, срок жизни пользовательского токена - 1 час.
  Токены выдаёт Booking Service; для вызовов Hotel он выпускает отдельный сервисный токен (`scope=internal`).
- **Resource Server в каждом сервисе**: Gateway - лишь первый рубеж; Hotel и Booking заново проверяют
  подпись и срок токена (`ResourceServerAutoConfiguration` в `common`).
- **Методная безопасность**: `@EnableMethodSecurity` + `@PreAuthorize` на контроллерах; роли USER/ADMIN
  с иерархией `ADMIN > USER`; внутренние эндпойнты - только `SCOPE_internal` (даже ADMIN получает 403).
- **Статусы**: 401 - нет токена, токен просрочен или подписан другим ключом, неверный пароль;
  403 - недостаточно прав. Оба ответа - в едином формате ошибок с `WWW-Authenticate: Bearer`.
- Пароли хранятся как BCrypt-хэш.

## 7. Тестирование

```bash
./gradlew build      # сборка, линтер и все тесты
./gradlew test       # только тесты
./gradlew check      # тесты и Checkstyle
```

JUnit 5 + MockMvc (сервисы) и WebTestClient (Gateway), реальная цепочка Spring Security и подписанные токены.

| Что проверяется                                                                                                                                | Тесты                                                                                              |
|------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| REST, валидация, коды ошибок, пагинация                                                                                                        | `*ControllerTest`                                                                                  |
| 401/403, роли, внутренние эндпойнты                                                                                                            | `HotelControllerTest`, `RoomReservationControllerTest`, `UserControllerTest`, `AuthControllerTest` |
| Сага: успех, отказ, тайм-аут, компенсация, повтор запроса, автоподбор                                                                          | `BookingServiceTest`, `BookingControllerTest`                                                      |
| Восстановление зависших `PENDING`, гонка с незавершённой сагой                                                                                 | `PendingBookingRecoveryTest`                                                                       |
| Таймауты, Retry, Circuit Breaker, сервисный токен и traceId в вызовах Hotel                                                                    | `HttpHotelClientTest`                                                                              |
| Конкурентные бронирования одного номера и дубли запроса                                                                                        | `RoomReservationServiceTest`, `BookingServiceTest`                                                 |
| Gateway: 401 на входе, закрытые внутренние маршруты, `X-Trace-Id`                                                                              | `ApiGatewayApplicationTests`                                                                       |
| Сквозные сценарии на настоящих сервисах: занятый номер и его освобождение отменой, автоподбор, параллельные брони, повтор запроса, отказ Hotel | `BookingEndToEndTest` (`e2e-tests`)                                                                |

## 8. Структура баз данных

Полное описание таблиц, ограничений и индексов в файле `docs/database.md`.

- Booking: `users`, `bookings`; Hotel: `hotels`, `rooms`, `room_reservations` (блокировки слотов саги).
- Сверх минимальной схемы: `request_id` с уникальными ключами (идемпотентность), `status` + `cancel_reason`,
  `version` (оптимистическая блокировка), `created_at`/`updated_at`, `city` у отеля, внешние ключи внутри
  сервиса.
- Индексы под запросы: история пользователя `(user_id, created_at)`, пересечения дат
  `(room_id, start_date, end_date)`, рекомендации `(times_booked, id)`, фильтр отелей по городу.
- Между сервисами - только ссылки по идентификаторам, без внешних ключей.

## 9. Демо-данные

Загружаются при старте в пустую базу (`DEMO_DATA=true`). Идентификаторы бронирований и номеров в двух
сервисах согласованы, данные подобраны под демонстрацию бизнес-логики.

| Пользователь | Пароль     | Роль  |
|--------------|------------|-------|
| `admin`      | `admin123` | ADMIN |
| `user`       | `user123`  | USER  |
| `alice`      | `alice123` | USER  |

| Отель (id)                       | Номера (id: номер, times_booked)                                    |
|----------------------------------|---------------------------------------------------------------------|
| 1 Арбат Плаза, Москва            | 1: 101 (5), 2: 102 (1), 3: 103 (0), 4: 201 (3), 5: 202 - на ремонте |
| 2 Тверская Резиденция, Москва    | 6: 11 (2), 7: 12 (0)                                                |
| 3 Невский Берег, Санкт-Петербург | 8: 1 (4), 9: 2 (1)                                                  |

Бронирования пользователя `user`:

- №1 - номер 1, с завтрашнего дня на 3 ночи, `CONFIRMED`: номер 1 занят, его бронирование на эти даты -> 409;
  отмена бронирования №1 освобождает номер в Hotel Service;
- №2 - номер 4, 10 дней назад, `CONFIRMED`: видно в статистике загрузки за прошлый период;
- №3 - номер 2, `CANCELLED` (`USER_CANCELLED`).

`GET /api/rooms/recommend` возвращает свободные номера по возрастанию `times_booked`, номер 5 не попадает
ни в один список; `alice` не видит бронирования `user` (404).

## 10. Качество кода

- **Слои**: контроллер -> интерфейс сервиса (`service`) -> реализация (`service/impl`) -> репозиторий; DTO
  отделены от сущностей, маппинг - MapStruct (`mapper`).
- **Зависимости через интерфейсы**: `HotelClient` (реализация `HttpHotelClient`), `BookingService`,
  `TokenService`, `RoomReservationService` и т.д. - в тестах подменяются моками.
- **Без «божественных» классов**: сага разделена на `BookingSaga` (шаги и компенсация), `BookingStateManager`
  (переходы статусов) и `PendingBookingRecovery` (восстановление зависших бронирований).
- **DRY**: общий код вынесен в модуль `common` - `ErrorResponse`, роли, валидация дат, контракт Hotel API и
  автоконфигурации обработки ошибок, трассировки, Resource Server, OpenAPI; сервисы его не дублируют.

### Общие классы модуля `common`

- `ErrorResponse` — единый формат ошибки всех сервисов и Gateway; `PageResponse` — формат страницы списка.
- `NotFoundException` (404), `ConflictException` (409), `ServiceUnavailableException` (503) — исключения бизнес-логики.
- `Role` — роли пользователей (USER, ADMIN).
- `@ValidDateRange` / `DateRange` — проверка `endDate > startDate` для запросов с периодом.
- `contract.hotel.*` — DTO внутренних эндпойнтов Hotel Service (`confirm-availability`, `release`),
  общие для вызывающей и вызываемой стороны.
- `trace.*`, `security.*`, `web.*`, `openapi.*` — трассировка `X-Trace-Id`, JWT и автоконфигурации
  веб-слоя сервисов (ADR-3).

## 11. Код-стиль

- Единый стиль проверяется Checkstyle (`config/checkstyle/checkstyle.xml`) в `./gradlew check`/`build` с нулём
  предупреждений: строки ≤ 120 символов, методы ≤ 40 строк, порядок импортов.
- Пакеты повторяют архитектуру: `controller`, `service`, `service/impl`, `repository`, `entity`, `dto`,
  `mapper`, `client`, `security`, `demo` в каждом сервисе; `ru.mephi.hotelbooking.<модуль>`.

## 12. Обработка ошибок

Глобальный `@RestControllerAdvice` из `common` и обработчики Gateway и Spring Security возвращают единый
`ErrorResponse`; бизнес-ошибки - собственные исключения с явным статусом.

Формат ответа с описанием ошибки:

```json
{
    "timestamp": "2030-01-01T10:00:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Validation failed",
    "path": "/api/bookings/booking",
    "traceId": "my-trace-0001",
    "details": [
        {
            "field": "startDate",
            "message": "must be a date in the present or in the future"
        }
    ]
}
```

| Статус | Когда                                                                                 |
|--------|---------------------------------------------------------------------------------------|
| 400    | ошибка валидации, некорректный JSON или параметр, неизвестное поле сортировки         |
| 401    | нет токена, токен просрочен или подписан другим ключом; неверный пароль               |
| 403    | недостаточно прав (роль, внутренний эндпойнт)                                         |
| 404    | ресурс не найден или принадлежит другому пользователю                                 |
| 409    | номер занят на эти даты, дубликат, `requestId` чужого запроса, конкурентное изменение |
| 503    | Hotel Service недоступен (после повторов или при разомкнутом circuit breaker)         |

## 13. Логирование и трассировка

Gateway присваивает запросу `X-Trace-Id` (или принимает его от клиента), сервисы пишут его в каждую строку
лога и передают дальше, клиент получает его в заголовке и в теле ошибки. Шаги саги дополнительно помечены
`bookingId`. Логируются ключевые события: переходы статусов бронирования, удержание и освобождение слотов,
отказы вызовов Hotel и компенсации, ошибки, access-лог Gateway.

В Docker логи пишутся в JSON (Elastic Common Schema), `traceId` и `bookingId` - отдельные поля:

```json
{
    "@timestamp": "...",
    "log": {
        "level": "INFO",
        "logger": "ru.mephi.hotelbooking.booking.service.BookingSaga"
    },
    "service": {
        "name": "booking-service"
    },
    "message": "Booking 5 room 3 held, reservation 4",
    "traceId": "my-trace-0001",
    "bookingId": "5"
}
```

При локальном запуске - текстовый формат `[traceId,bookingId]`:

```
[booking-service] [my-trace-0001,5] BookingSaga : Booking 5 confirming availability of room 3, requestId=booking-5-room-3
[hotel-service]   [my-trace-0001,5] RoomReservationServiceImpl : Room 3 held for booking 5 [...], timesBooked=1
[booking-service] [my-trace-0001,5] BookingStateManager : Booking 5 status PENDING -> CONFIRMED, room=3
[api-gateway]     [my-trace-0001] POST /api/bookings/booking -> 201 CREATED (115 ms)
```

## 14. Архитектура и документация

```
              клиент
                │  JWT, X-Trace-Id
                v
        ┌───────────────┐   регистрация / discovery  ┌───────────────┐
        │  api-gateway  │ ─────────────────────────> │ eureka-server │
        │     :8080     │                            │     :8761     │
        └───────────────┘                            └───────────────┘
           │ lb://              │ lb://                      ^
           v                    v                            │
  ┌─────────────────┐  сага (сервисный JWT,  ┌───────────────┐
  │ booking-service │  Retry/Timeout/CB)     │ hotel-service │
  │   :8082, H2     │ ─────────────────────> │   :8081, H2   │
  └─────────────────┘                        └───────────────┘
```

| Модуль            | Порт | Назначение                                                                                                                  |
|-------------------|------|-----------------------------------------------------------------------------------------------------------------------------|
| `common`          | -    | ErrorDTO, роли, валидация дат, контракт Hotel API; автоконфигурации обработки ошибок, трассировки, Resource Server, OpenAPI |
| `eureka-server`   | 8761 | Service Registry                                                                                                            |
| `api-gateway`     | 8080 | Маршрутизация через Eureka (`lb://`), проверка JWT на входе, `X-Trace-Id`                                                   |
| `hotel-service`   | 8081 | Отели и номера, рекомендации, статистика загрузки, блокировка слотов                                                        |
| `booking-service` | 8082 | Регистрация и выдача JWT, пользователи, бронирования, сага с компенсацией                                                   |
| `e2e-tests`       | -    | Сквозные тесты: настоящие Hotel и Booking в одном JVM                                                                       |

Ключевые решения (ADR):

- [ADR-1](docs/adr/1-api-routing.md) - внешние пути API и маршрутизация;
- [ADR-2](docs/adr/2-booking-saga.md) - сага бронирования, идемпотентность, Retry/Timeout/CircuitBreaker;
- [ADR-3](docs/adr/3-security-and-tracing.md) - JWT, роли, доступ к внутренним эндпойнтам, трассировка.

Схема баз данных - [docs/database.md](docs/database.md); API - Swagger UI каждого сервиса (см. [Запуск](#запуск)).

## 15. Поддерживаемость тестов

- **Независимость от окружения**: H2 in-memory, Eureka отключена (`src/test/resources/config/application.yml`),
  сеть и Docker не нужны.
- **Разумные моки**: в модульных тестах Booking сервис Hotel заменён моком `HotelClient` или HTTP-заглушкой
  (`HttpHotelClientTest`), в `e2e-tests` - настоящий; Spring Security не мокается - тесты отправляют
  реально подписанные токены.
- **Читаемость**: имена тестов описывают сценарий (`requestWithoutTokenIsRejectedAtEntry`,
  `transientErrorsAreRetried`), повторяющиеся шаги (получение токена, запросы) вынесены в хелперы.
