# booking-service

Порт 8082. H2: users и bookings. UserController/UserService отвечают за регистрацию, вход и CRUD пользователей. BookingController предоставляет личные операции; BookingStore фиксирует PENDING и читает историю. BookingSaga выполняет локальные шаги согласованности, BookingService координирует повтор выбора. HotelClient изолирует HTTP, HotelHttpClient реализует timeout/retry/circuit breaker. CompensationRecovery повторяет незавершённые операции. UserSeed создаёт пользователей и исторические брони.

Swagger: http://localhost:8082/swagger-ui/index.html. Доступ через Gateway: /user/**, /booking/**, /bookings, /api/bookings/**. Для запуска требуется Eureka и Hotel Service; независимые тесты используют mock HotelClient и локальный HTTP-сервер.

Команды сборки и запуска я описал в [корневом README](../README.md). Полный API — в Swagger бизнес-сервисов и таблицах корневого README.
