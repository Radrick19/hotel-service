package ru.student.hotel.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id=:id")
  Optional<Booking> lockById(UUID id);

  Page<Booking> findByUserId(Long userId, Pageable pageable);

  boolean existsByUserId(Long userId);

  @Query(
      "select b.id from Booking b where b.releasePending=true or (b.status=ru.student.hotel.booking.Booking$Status.PENDING and b.createdAt<:before)")
  List<UUID> recoverable(Instant before);
}
