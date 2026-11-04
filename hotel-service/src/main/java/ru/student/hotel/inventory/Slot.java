package ru.student.hotel.inventory;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "room_slots",
    indexes = @Index(name = "idx_slot_dates", columnList = "room_id,start_date,end_date"))
@Getter
@Setter
@NoArgsConstructor
public class Slot {
  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "room_id")
  private Room room;

  private LocalDate startDate;
  private LocalDate endDate;

  @Column(nullable = false)
  private boolean released;

  public Slot(UUID id, Room room, LocalDate start, LocalDate end, boolean released) {
    this.id = id;
    this.room = room;
    this.startDate = start;
    this.endDate = end;
    this.released = released;
  }
}
