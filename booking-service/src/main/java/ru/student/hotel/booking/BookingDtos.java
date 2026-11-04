package ru.student.hotel.booking;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class BookingDtos {
  private BookingDtos() {}

  public record Credentials(
      @NotBlank @Size(min = 3, max = 80) String username,
      @NotBlank @Size(min = 8, max = 100) String password) {}

  public record UserInput(
      @NotBlank @Size(min = 3, max = 80) String username,
      @NotBlank @Size(min = 8, max = 100) String password,
      @NotNull User.Role role) {}

  public record UserUpdate(
      @NotNull @Positive Long id,
      @NotBlank @Size(min = 3, max = 80) String username,
      @NotBlank @Size(min = 8, max = 100) String password,
      @NotNull User.Role role) {}

  public record UserView(Long id, String username, User.Role role) {
    public static UserView of(User u) {
      return new UserView(u.getId(), u.getUsername(), u.getRole());
    }
  }

  public record Token(String token, String tokenType, long expiresIn) {}

  public record BookingInput(
      @NotNull UUID requestId,
      Long roomId,
      Long hotelId,
      boolean autoSelect,
      @NotNull @FutureOrPresent LocalDate startDate,
      @NotNull LocalDate endDate) {
    @AssertTrue(message = "endDate must follow startDate, roomId required for manual selection")
    public boolean isValid() {
      return startDate != null
          && endDate != null
          && startDate.isBefore(endDate)
          && (autoSelect || (roomId != null && roomId > 0))
          && (hotelId == null || hotelId > 0);
    }
  }

  public record BookingView(
      UUID id,
      Long userId,
      Long roomId,
      LocalDate startDate,
      LocalDate endDate,
      Booking.Status status,
      Instant createdAt,
      boolean releasePending,
      int failureStatus) {
    public static BookingView of(Booking b) {
      return new BookingView(
          b.getId(),
          b.getUser().getId(),
          b.getRoomId(),
          b.getStartDate(),
          b.getEndDate(),
          b.getStatus(),
          b.getCreatedAt(),
          b.isReleasePending(),
          b.getFailureStatus());
    }
  }

  public record History(
      List<BookingView> content, int page, int size, long totalElements, int totalPages) {}

  public record RoomView(
      Long id, Long hotelId, String number, boolean available, long timesBooked) {}
}
