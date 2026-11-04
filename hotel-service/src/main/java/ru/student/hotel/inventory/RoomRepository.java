package ru.student.hotel.inventory;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RoomRepository extends JpaRepository<Room, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.id=:id")
  Optional<Room> lockById(Long id);

  boolean existsByHotelId(Long id);
}
