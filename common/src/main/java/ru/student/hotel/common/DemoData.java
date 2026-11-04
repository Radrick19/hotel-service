package ru.student.hotel.common;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

public final class DemoData {
  private DemoData() {}

  public static UUID bookingId(int index) {
    return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", index));
  }

  public static Long roomId(int index) {
    return index == 3 ? 2L : 1L;
  }

  public static LocalDate start(int index) {
    return LocalDate.now().minusDays(30L + index * 3L);
  }

  public static UUID slotId(int index) {
    return UUID.nameUUIDFromBytes(
        (bookingId(index) + ":" + roomId(index)).getBytes(StandardCharsets.UTF_8));
  }
}
