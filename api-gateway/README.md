# api-gateway

Порт 8080. Spring Cloud Gateway WebFlux. Маршруты lb://booking-service и lb://hotel-service разрешаются через Eureka и Spring Cloud LoadBalancer. Авторизация/регистрация доступны публично, остальные запросы требуют валидный JWT. Методные роли и владение проверяют backend-сервисы. Внутренние confirm/release исключены из маршрутов и запрещены security-фильтром; X-Internal-Key удаляется из проксируемых запросов.

GatewayTraceFilter передаёт и возвращает X-Request-Id, выводит исход запроса в лог. База данных не требуется. Набор маршрутов находится в GatewayConfig. Тесты — WebTestClient и проверка RouteLocator; ручная проверка реального проксирования через Docker Compose описана в корневом README.

Команды сборки и запуска я описал в [корневом README](../README.md). Полный API — в Swagger бизнес-сервисов и таблицах корневого README.
