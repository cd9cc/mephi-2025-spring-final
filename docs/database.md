# Схема баз данных

Каждый сервис использует собственную in-memory H2. Схема создаётся Hibernate по JPA-сущностям
(`ddl-auto: create-drop`). Связи между базами разных сервисов — только по идентификаторам, без внешних ключей.

Периоды проживания везде задаются полуинтервалом `[start_date, end_date)`: `end_date` — день выезда,
поэтому бронирования «10-12» и «12-14» одного номера не пересекаются.

## Booking Service

### users

| Колонка    | Тип               | Ограничения                   | Описание             |
|------------|-------------------|-------------------------------|----------------------|
| id         | bigint identity   | PK                            |                      |
| username   | varchar(50)       | not null, `uk_users_username` | логин                |
| password   | varchar(100)      | not null                      | BCrypt-хэш           |
| role       | enum(USER, ADMIN) | not null                      | роль, попадает в JWT |
| created_at | timestamp with tz | not null                      | дата регистрации     |

### bookings

| Колонка       | Тип                                                                           | Ограничения                        | Описание                                             |
|---------------|-------------------------------------------------------------------------------|------------------------------------|------------------------------------------------------|
| id            | bigint identity                                                               | PK                                 | сквозной идентификатор саги (`bookingId`)            |
| user_id       | bigint                                                                        | not null, FK `fk_bookings_user`    | владелец бронирования                                |
| room_id       | bigint                                                                        | not null                           | номер в Hotel Service                                |
| start_date    | date                                                                          | not null                           | день заезда (включительно)                           |
| end_date      | date                                                                          | not null                           | день выезда (не включительно)                        |
| status        | enum(PENDING, CONFIRMED, CANCELLED)                                           | not null                           | состояние саги                                       |
| cancel_reason | enum(ROOM_UNAVAILABLE, HOTEL_UNAVAILABLE, PROCESSING_TIMEOUT, USER_CANCELLED) | null для неотменённых              | причина отмены; определяет ответ при повторе запроса |
| request_id    | varchar(64)                                                                   | not null, `uk_bookings_request_id` | ключ идемпотентности от клиента                      |
| version       | bigint                                                                        | not null                           | оптимистическая блокировка                           |
| created_at    | timestamp with tz                                                             | not null                           |                                                      |
| updated_at    | timestamp with tz                                                             | not null                           |                                                      |

Индексы:

- `idx_bookings_user_created (user_id, created_at)` — история бронирований пользователя;
- `idx_bookings_room_dates (room_id, start_date, end_date)` — поиск пересекающихся бронирований.

## Hotel Service

### hotels

| Колонка    | Тип               | Ограничения | Описание                                    |
|------------|-------------------|-------------|---------------------------------------------|
| id         | bigint identity   | PK          |                                             |
| name       | varchar(255)      | not null    |                                             |
| address    | varchar(500)      | not null    |                                             |
| city       | varchar(100)      |             | фильтр отелей по городу (`idx_hotels_city`) |
| created_at | timestamp with tz | not null    |                                             |

### rooms

| Колонка      | Тип               | Ограничения                                          | Описание                                                        |
|--------------|-------------------|------------------------------------------------------|-----------------------------------------------------------------|
| id           | bigint identity   | PK                                                   |                                                                 |
| hotel_id     | bigint            | not null, FK `fk_rooms_hotel`                        |                                                                 |
| number       | varchar(20)       | not null, `uk_rooms_hotel_number (hotel_id, number)` | номер комнаты, уникален в пределах отеля                        |
| capacity     | integer           | not null                                             | вместимость                                                     |
| available    | boolean           | not null                                             | операционная доступность (ремонт и т.п.), не занятость по датам |
| times_booked | bigint            | not null                                             | счётчик успешных бронирований для равномерной загрузки          |
| version      | bigint            | not null                                             | оптимистическая блокировка                                      |
| created_at   | timestamp with tz | not null                                             |                                                                 |

Индекс `idx_rooms_times_booked (times_booked, id)` соответствует сортировке рекомендаций.

### room_reservations

Временная блокировка слота, создаваемая шагом саги `confirm-availability`. Занятость номера на даты
определяется по активным записям этой таблицы.

| Колонка    | Тип                    | Ограничения                                 | Описание                                  |
|------------|------------------------|---------------------------------------------|-------------------------------------------|
| id         | bigint identity        | PK                                          |                                           |
| room_id    | bigint                 | not null, FK `fk_room_reservations_room`    |                                           |
| booking_id | bigint                 | not null                                    | бронирование в Booking Service            |
| request_id | varchar(64)            | not null, `uk_room_reservations_request_id` | ключ идемпотентности шага саги            |
| start_date | date                   | not null                                    |                                           |
| end_date   | date                   | not null                                    | не включительно                           |
| status     | enum(ACTIVE, RELEASED) | not null                                    | `RELEASED` — после компенсации или отмены |
| created_at | timestamp with tz      | not null                                    |                                           |
| updated_at | timestamp with tz      | not null                                    |                                           |

Индексы:

- `idx_room_reservations_room_dates (room_id, start_date, end_date)` — проверка пересечений при блокировке;
- `idx_room_reservations_booking_id (booking_id)` — поиск блокировки при `release`.

## Демо-данные

При `demo-data.enabled=true` (по умолчанию, отключается `DEMO_DATA=false`; в тестах отключено) пустые базы
заполняются классами `DemoDataLoader` обоих сервисов: 3 пользователя, 3 отеля, 9 номеров, 3 бронирования с
соответствующими блокировками слотов. Состав — в README, раздел «Демо-данные».
