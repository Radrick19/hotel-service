package ru.student.hotel.booking;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class CompensationRecovery {
  private final BookingService service;
  private final BookingRepository bookings;
  private final BookingSaga saga;

  @Scheduled(fixedDelayString = "${booking.compensation-delay-ms}")
  public void recover() {
    for (var id : bookings.recoverable(Instant.now().minusSeconds(30))) {
      try {
        service.resume(id);
        saga.retryRelease(id);
      } catch (RuntimeException e) {
        log.warn("event=recovery_failed bookingId={}", id, e);
      }
    }
  }
}
