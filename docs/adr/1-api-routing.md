# ADR-1. Внешние пути API и маршрутизация через Gateway

## Контекст

ТЗ задаёт маршруты Gateway `/api/bookings/** -> Booking Service` и `/api/hotels/** -> Hotel Service`,
а эндпойнты Booking Service - как `/booking`, `/bookings`, `/user/...`. Эти пути не совпадают с
маршрутом Gateway. Кроме того, пользовательские эндпойнты номеров (`GET /api/rooms`,
`GET /api/rooms/recommend`) не покрыты маршрутом `/api/hotels/**`, а внутренние эндпойнты саги
(`confirm-availability`, `release`) не должны публиковаться через Gateway.

Рассмотренные варианты:

1. Пути сервиса строго по ТЗ + отдельные маршруты Gateway на `/user/**`, `/booking/**`, `/bookings` -
   нет единого префикса `/api`, корневые пути конфликтуют между сервисами.
2. Пути сервиса строго по ТЗ + `StripPrefix` на Gateway - внешние и внутренние пути расходятся
   (Swagger, логи), появляются пути вида `/api/bookings/bookings`.
3. Единый префикс `/api/bookings` у всех контроллеров Booking Service, Gateway проксирует пути
   без переписывания.

## Решение

Выбран вариант 3.

- Все контроллеры Booking Service работают под префиксом `/api/bookings`, остаток пути - как в ТЗ:

  | ТЗ                    | Фактический путь                   |
  |-----------------------|------------------------------------|
  | `POST /user/register` | `POST /api/bookings/user/register` |
  | `POST /user/auth`     | `POST /api/bookings/user/auth`     |
  | `POST/PATCH /user`    | `POST/PATCH /api/bookings/user` (id - в теле PATCH) |
  | `DELETE /user`        | `DELETE /api/bookings/user?id={id}` |
  | - (дополнительно)     | `GET /api/bookings/user`, `GET /api/bookings/user/{id}` (ADMIN) |
  | `POST /booking`       | `POST /api/bookings/booking`       |
  | `GET /booking/{id}`   | `GET /api/bookings/booking/{id}`   |
  | `DELETE /booking/{id}`| `DELETE /api/bookings/booking/{id}`|
  | `GET /bookings`       | `GET /api/bookings`                |

- Gateway:
    - `/api/bookings/**` -> `lb://booking-service`;
    - `/api/hotels/**`, `/api/rooms/**` -> `lb://hotel-service`;
    - `/api/rooms/*/confirm-availability`, `/api/rooms/*/release` -> `404` на Gateway (маршрут-заглушка,
      объявлен раньше маршрута Hotel Service). Booking Service вызывает их напрямую через Eureka.

## Последствия

- Пути одинаковы на Gateway и в сервисах: Swagger, логи и тесты используют один и тот же URL.
- Пути Booking Service отличаются от ТЗ префиксом `/api/bookings` (ТЗ допускает это - структура
  эндпойнтов в нём названа примерной).
- Внутренние эндпойнты Hotel Service дополнительно защищаются на уровне самого сервиса
  (Resource Server), блокировка на Gateway - лишь первый рубеж.
