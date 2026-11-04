# Проверка hotel Service

Для проверки проекта использую JUnit 5, MockMvc и WebTestClient. Подробные команды автоматической и ручной проверки привожу в README.

| Набор | Тестов | Провалов | Ошибок |
|---|---:|---:|---:|
| GatewayIntegrationTest | 4 | 0 | 0 |
| BookingIntegrationTest | 14 | 0 | 0 |
| HotelHttpClientTest | 4 | 0 | 0 |
| InventoryIntegrationTest | 12 | 0 | 0 |

Результат проверки: **34 теста, 0 провалов, 0 ошибок**. Проверка форматирования Spotless входит в фазу Maven verify.

## Команды проверки

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps

docker build --target build -t hotel-service-build .
docker run --rm hotel-service-build mvn -B verify

docker compose down
```

При проверке контейнеров контролирую готовность четырёх приложений и регистрацию клиентов в Eureka с именами hotel-service, booking-service и api-gateway. Через Gateway проверяю вход по JWT, выдачу отелей, подтверждение брони, идемпотентный повтор и отмену с releasePending=false.

При локальном запуске тестов использую `mvn clean verify`; отчёты находятся в `*/target/surefire-reports/`.
