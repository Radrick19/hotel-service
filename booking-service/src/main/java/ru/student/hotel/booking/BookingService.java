package ru.student.hotel.booking;

import static ru.student.hotel.booking.BookingDtos.*;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingService {
  private final BookingStore store;
  private final BookingSaga saga;

  public BookingView create(Long userId, BookingInput in) {
    try {
      store.create(userId, in);
    } catch (DataIntegrityViolationException duplicate) {
      store.validate(userId, in);
    }
    store.validate(userId, in);
    return resume(in.requestId());
  }

  public BookingView resume(UUID id) {
    // Conflicting automatic selection can choose another room; bound the work per request.
    for (int attempt = 0; attempt < 10; attempt++) {
      saga.prepare(id);
      var result = saga.process(id);
      if (result.status() != Booking.Status.PENDING) return result;
    }
    return saga.cancel(id, null);
  }
}
