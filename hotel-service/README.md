# hotel-service

Порт 8081. H2: hotels, rooms, room_slots. HotelController и RoomController проверяют роли и входные данные; InventoryService реализует CRUD, календарную доступность, рекомендации, статистику, атомарный confirm/release. Изменение слота и счётчика защищено блокировкой Room. InventorySeed создаёт два отеля, пять номеров и исторические слоты.

Swagger: http://localhost:8081/swagger-ui/index.html. Публичные маршруты: /api/hotels/**, /api/rooms/**. POST confirm-availability/release доступны только напрямую по INTERNAL JWT и внутреннему ключу. Прямой доступ также требует JWT. Удаление номера с историей запрещено; используйте available=false.

Команды сборки и запуска я описал в [корневом README](../README.md). Полный API — в Swagger бизнес-сервисов и таблицах корневого README.
