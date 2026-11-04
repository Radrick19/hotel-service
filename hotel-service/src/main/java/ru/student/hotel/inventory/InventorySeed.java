package ru.student.hotel.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventorySeed implements CommandLineRunner {
  private final InventoryService service;
  private final HotelRepository hotels;
  private final RoomRepository rooms;

  @Override
  @Transactional
  public void run(String... args) {
    if (hotels.count() > 0) return;
    var first = hotels.save(new Hotel("Nevsky Hotel", "Saint Petersburg, Nevsky prospect 10"));
    var second = hotels.save(new Hotel("Volga Hotel", "Kazan, Central street 5"));
    rooms.save(new Room(first, "101"));
    rooms.save(new Room(first, "102"));
    rooms.save(new Room(first, "103"));
    rooms.save(new Room(second, "201"));
    var repair = new Room(second, "202");
    repair.setAvailable(false);
    rooms.save(repair);
    for (int i = 1; i <= 3; i++)
      service.confirm(
          ru.student.hotel.common.DemoData.roomId(i),
          new InventoryDtos.SlotInput(
              ru.student.hotel.common.DemoData.slotId(i),
              ru.student.hotel.common.DemoData.start(i),
              ru.student.hotel.common.DemoData.start(i).plusDays(2)));
  }
}
