package ru.student.hotel.inventory;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SlotRepository extends JpaRepository<Slot, UUID> {
  @Query(
      "select count(s)>0 from Slot s where s.room.id=:roomId and s.released=false and s.startDate<:end and s.endDate>:start")
  boolean overlaps(Long roomId, LocalDate start, LocalDate end);

  @Query("select s from Slot s where s.released=false and s.startDate<:end and s.endDate>:start")
  List<Slot> occupied(LocalDate start, LocalDate end);

  boolean existsByRoomId(Long roomId);
}
