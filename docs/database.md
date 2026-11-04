# Схема данных

Базы H2 изолированы: сервисы не читают таблицы друг друга. Схема создаётся JPA (`create-drop`). Идентификаторы и внешние ключи между сервисами проверяются через API, а не межбазовыми JOIN.

## Booking Service — jdbc:h2:mem:booking-service

| Таблица | Поля |
|---|---|
| users | id BIGINT PK identity; username VARCHAR(80) UNIQUE NOT NULL; password VARCHAR NOT NULL (BCrypt); role VARCHAR NOT NULL (USER/ADMIN) |
| bookings | id UUID PK (= requestId); user_id BIGINT FK users NOT NULL; room_id BIGINT NULL; requested_room_id BIGINT NULL; hotel_id BIGINT NULL; auto_select BOOLEAN; start_date DATE NOT NULL; end_date DATE NOT NULL; status VARCHAR NOT NULL; created_at TIMESTAMP NOT NULL; release_pending BOOLEAN; failure_status INTEGER |

Индексы: `(user_id, created_at)` — история; `(status, release_pending)` — восстановление. `room_id` допускает NULL до выбора номера и при отсутствии кандидатов. `requested_room_id`, `hotel_id`, `auto_select` и даты сохраняют исходный запрос для проверки идемпотентности. При автоподборе requested_room_id=NULL, при ручном выборе hotel_id=NULL, потому что эти поля запроса соответственно игнорируются.

`failure_status` сохраняет HTTP-код отказа для одинакового ответа на повтор отменённого создания. Статусы — PENDING, CONFIRMED, CANCELLED. Отмена подтверждённой брони допускает CONFIRMED → CANCELLED. `release_pending` сохраняет необходимость повторить компенсацию.

Связь User 1:N Booking. Удаление пользователя с историей запрещено (409), каскадного удаления броней нет.

## Hotel Service — jdbc:h2:mem:hotel-service

| Таблица | Поля |
|---|---|
| hotels | id BIGINT PK identity; name VARCHAR NOT NULL; address VARCHAR NOT NULL |
| rooms | id BIGINT PK identity; hotel_id BIGINT FK hotels NOT NULL; number VARCHAR NOT NULL; available BOOLEAN NOT NULL; times_booked BIGINT NOT NULL |
| room_slots | id UUID PK (идемпотентный ключ слота); room_id BIGINT FK rooms NOT NULL; start_date DATE NULL; end_date DATE NULL; released BOOLEAN NOT NULL |

Ограничение UNIQUE `(hotel_id, number)` исключает одинаковые номера внутри одного отеля. Индекс `(room_id, start_date, end_date)` поддерживает поиск занятости. Отношения Hotel 1:N Room 1:N Slot. Каскадное удаление отсутствует: отель с номерами и номер с историей слотов удалять нельзя.

`room_slots` — локальная проекция занятости, необходимая для атомарного confirm без обращения к чужой БД. Даты NULL допустимы только для отметки release, пришедшей раньше confirm. Такие строки released=true не участвуют в занятости. Интервалы пересекаются при `existing.start < new.end AND existing.end > new.start`; операционная available проверяется отдельно.

Перед изменением слотов и счётчика блокируется строка Room. Проверка пересечения, запись слота и изменение счётчика происходят в одной локальной транзакции. Это исключает двойное бронирование даже разных requestId и предотвращает потерю обновления счётчика.

`times_booked` увеличивается один раз для нового подтверждения и уменьшается один раз при первом освобождении. Ранее освобождённый ключ не может снова подтвердиться. Исторические неотменённые брони продолжают учитываться в счётчике.

Все даты бронирования — LocalDate, время создания — Instant (UTC). Проверки API: заезд не раньше текущего дня при создании, выезд строго после заезда, обязательные поля и длины строк. Для in-memory БД использую создание схемы через JPA без дополнительных миграций.
