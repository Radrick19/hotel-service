package ru.student.hotel.booking;

import static ru.student.hotel.booking.BookingDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('USER')")
public class BookingController {
  private final BookingService service;
  private final BookingStore store;
  private final BookingSaga saga;

  @PostMapping({"/booking", "/api/bookings"})
  public ResponseEntity<BookingView> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingInput in) {
    var b = service.create(Long.valueOf(jwt.getSubject()), in);
    return ResponseEntity.status(
            b.status() == Booking.Status.CANCELLED
                ? HttpStatus.valueOf(b.failureStatus())
                : HttpStatus.CREATED)
        .body(b);
  }

  @GetMapping({"/bookings", "/api/bookings"})
  public History history(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return store.history(Long.valueOf(jwt.getSubject()), page, size);
  }

  @GetMapping({"/booking/{id}", "/api/bookings/{id}"})
  public BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return store.get(id, Long.valueOf(jwt.getSubject()));
  }

  @DeleteMapping({"/booking/{id}", "/api/bookings/{id}"})
  public BookingView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return saga.cancel(id, Long.valueOf(jwt.getSubject()));
  }
}
