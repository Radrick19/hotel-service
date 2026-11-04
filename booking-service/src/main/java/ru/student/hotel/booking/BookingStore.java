package ru.student.hotel.booking;

import static ru.student.hotel.booking.BookingDtos.*;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.student.hotel.common.ApiException;

@Service
@RequiredArgsConstructor
public class BookingStore {
  private final BookingRepository bookings;
  private final UserRepository users;

  @Transactional
  public void create(Long userId, BookingInput in) {
    if (bookings.existsById(in.requestId())) return;
    Booking b = new Booking();
    b.setId(in.requestId());
    b.setUser(users.findById(userId).orElseThrow(() -> ApiException.missing("User not found")));
    b.setAutoSelect(in.autoSelect());
    b.setRequestedRoomId(in.autoSelect() ? null : in.roomId());
    b.setHotelId(in.autoSelect() ? in.hotelId() : null);
    b.setStartDate(in.startDate());
    b.setEndDate(in.endDate());
    bookings.saveAndFlush(b);
  }

  @Transactional(readOnly = true)
  public void validate(Long userId, BookingInput in) {
    Booking b = bookings.findById(in.requestId()).orElseThrow();
    if (!b.getUser().getId().equals(userId)
        || b.isAutoSelect() != in.autoSelect()
        || !Objects.equals(b.getRequestedRoomId(), in.autoSelect() ? null : in.roomId())
        || !Objects.equals(b.getHotelId(), in.autoSelect() ? in.hotelId() : null)
        || !b.getStartDate().equals(in.startDate())
        || !b.getEndDate().equals(in.endDate()))
      throw ApiException.conflict("requestId already used with different request data");
  }

  @Transactional(readOnly = true)
  public BookingView get(UUID id, Long userId) {
    var b =
        bookings
            .findById(id)
            .filter(x -> x.getUser().getId().equals(userId))
            .orElseThrow(() -> ApiException.missing("Booking not found"));
    return BookingView.of(b);
  }

  @Transactional(readOnly = true)
  public History history(Long userId, int page, int size) {
    var result =
        bookings.findByUserId(
            userId,
            PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
    return new History(
        result.map(BookingView::of).toList(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }
}
