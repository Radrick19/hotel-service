package ru.student.hotel.booking;

import java.time.LocalDate;
import java.util.*;

public interface HotelClient {
  List<BookingDtos.RoomView> recommend(
      LocalDate start, LocalDate end, Long hotelId, UUID bookingId);

  void confirm(Long roomId, LocalDate start, LocalDate end, UUID bookingId);

  void release(Long roomId, UUID bookingId);

  static UUID slotId(UUID bookingId, Long roomId) {
    return UUID.nameUUIDFromBytes(
        (bookingId + ":" + roomId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
