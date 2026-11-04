package ru.student.hotel.booking;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "bookings",
    indexes = {
      @Index(name = "idx_booking_user", columnList = "user_id,created_at"),
      @Index(name = "idx_booking_recovery", columnList = "status,release_pending")
    })
@Getter
@Setter
@NoArgsConstructor
public class Booking {
  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  private Long roomId;
  private Long requestedRoomId;
  private Long hotelId;
  private boolean autoSelect;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  private boolean releasePending;
  private int failureStatus = 409;

  public enum Status {
    PENDING,
    CONFIRMED,
    CANCELLED
  }
}
