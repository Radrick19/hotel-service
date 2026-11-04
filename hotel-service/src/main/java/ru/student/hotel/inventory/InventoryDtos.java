package ru.student.hotel.inventory;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public final class InventoryDtos {
  private InventoryDtos() {}

  public record HotelInput(
      @NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 500) String address) {}

  public record HotelView(Long id, String name, String address) {
    public static HotelView of(Hotel h) {
      return new HotelView(h.getId(), h.getName(), h.getAddress());
    }
  }

  public record RoomInput(
      @NotNull @Positive Long hotelId,
      @NotBlank @Size(max = 40) String number,
      boolean available) {}

  public record RoomView(
      Long id, Long hotelId, String number, boolean available, long timesBooked) {
    public static RoomView of(Room r) {
      return new RoomView(
          r.getId(), r.getHotel().getId(), r.getNumber(), r.isAvailable(), r.getTimesBooked());
    }
  }

  public record SlotInput(
      @NotNull UUID requestId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

  public record ReleaseInput(@NotNull UUID requestId) {}

  public record RoomStats(
      Long roomId,
      Long hotelId,
      String number,
      long timesBooked,
      long bookingsInPeriod,
      long occupiedDays,
      double occupancyPercent) {}
}
