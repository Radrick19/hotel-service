package ru.student.hotel.booking;

import static ru.student.hotel.booking.BookingDtos.*;

import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.student.hotel.common.ApiException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSaga {
  private final BookingRepository bookings;
  private final HotelClient hotel;

  // Commit the chosen room BEFORE the remote confirmation, so recovery knows what to release.
  @Transactional
  public void prepare(UUID id) {
    Booking b = lock(id);
    if (b.getStatus() != Booking.Status.PENDING || b.getRoomId() != null) return;
    log.info("event=booking_pending bookingId={}", id);
    if (!b.isAutoSelect()) {
      b.setRoomId(b.getRequestedRoomId());
      return;
    }
    try {
      var candidates = hotel.recommend(b.getStartDate(), b.getEndDate(), b.getHotelId(), id);
      if (candidates.isEmpty()) {
        b.setStatus(Booking.Status.CANCELLED);
        log.info("event=no_available_rooms bookingId={}", id);
      } else b.setRoomId(candidates.get(0).id());
    } catch (RuntimeException e) {
      b.setStatus(Booking.Status.CANCELLED);
      b.setFailureStatus(503);
      log.warn("event=recommendation_failed bookingId={}", id, e);
    }
  }

  @Transactional
  public BookingView process(UUID id) {
    Booking b = lock(id);
    if (b.getStatus() != Booking.Status.PENDING) return BookingView.of(b);
    if (b.getRoomId() == null) return BookingView.of(b);
    try {
      hotel.confirm(b.getRoomId(), b.getStartDate(), b.getEndDate(), id);
      b.setStatus(Booking.Status.CONFIRMED);
      log.info("event=booking_confirmed bookingId={} roomId={}", id, b.getRoomId());
    } catch (RuntimeException e) {
      log.warn("event=booking_failed bookingId={} cause={}", id, e.toString());
      release(b);
      if (e instanceof ApiException api
          && api.status().value() == 409
          && b.isAutoSelect()
          && !b.isReleasePending()) {
        b.setRoomId(null);
        return BookingView.of(b);
      }
      b.setStatus(Booking.Status.CANCELLED);
      b.setFailureStatus(
          e instanceof ApiException api
                  && java.util.Set.of(400, 404, 409).contains(api.status().value())
              ? api.status().value()
              : 503);
      log.info("event=booking_cancelled bookingId={} releasePending={}", id, b.isReleasePending());
    }
    return BookingView.of(b);
  }

  @Transactional
  public BookingView cancel(UUID id, Long userId) {
    Booking b = lock(id);
    if (userId != null && !b.getUser().getId().equals(userId))
      throw ApiException.missing("Booking not found");
    b.setStatus(Booking.Status.CANCELLED);
    release(b);
    log.info("event=booking_cancelled bookingId={} releasePending={}", id, b.isReleasePending());
    return BookingView.of(b);
  }

  @Transactional
  public void retryRelease(UUID id) {
    var b = lock(id);
    if (b.getStatus() == Booking.Status.CANCELLED && b.isReleasePending()) release(b);
  }

  private Booking lock(UUID id) {
    return bookings.lockById(id).orElseThrow(() -> ApiException.missing("Booking not found"));
  }

  private void release(Booking b) {
    if (b.getRoomId() == null) return;
    try {
      hotel.release(b.getRoomId(), b.getId());
      b.setReleasePending(false);
      log.info("event=compensation_completed bookingId={}", b.getId());
    } catch (RuntimeException e) {
      b.setReleasePending(true);
      log.warn("event=compensation_pending bookingId={} cause={}", b.getId(), e.toString());
    }
  }
}
