package ru.student.hotel.inventory;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "hotel_id")
  private Hotel hotel;

  @Column(nullable = false)
  private String number;

  @Column(nullable = false)
  private boolean available = true;

  @Column(nullable = false)
  private long timesBooked;

  public Room(Hotel hotel, String number) {
    this.hotel = hotel;
    this.number = number;
  }
}
