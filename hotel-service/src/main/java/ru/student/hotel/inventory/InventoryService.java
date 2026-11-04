package ru.student.hotel.inventory;

import static ru.student.hotel.inventory.InventoryDtos.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.student.hotel.common.ApiException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {
  private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
  private final HotelRepository hotels;
  private final RoomRepository rooms;
  private final SlotRepository slots;

  public static void dates(LocalDate start, LocalDate end) {
    if (start == null || end == null || !start.isBefore(end))
      throw new ApiException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "startDate must be before endDate");
  }

  public List<HotelView> hotels(String search) {
    return hotels.findAll().stream()
        .filter(
            h ->
                search == null
                    || (h.getName() + " " + h.getAddress())
                        .toLowerCase(Locale.ROOT)
                        .contains(search.toLowerCase(Locale.ROOT)))
        .map(HotelView::of)
        .toList();
  }

  public HotelView hotel(Long id) {
    return HotelView.of(findHotel(id));
  }

  private Hotel findHotel(Long id) {
    return hotels.findById(id).orElseThrow(() -> ApiException.missing("Hotel not found"));
  }

  @Transactional
  public HotelView saveHotel(Long id, HotelInput in) {
    Hotel h = id == null ? new Hotel() : findHotel(id);
    h.setName(in.name());
    h.setAddress(in.address());
    return HotelView.of(hotels.save(h));
  }

  @Transactional
  public void deleteHotel(Long id) {
    Hotel h = findHotel(id);
    if (rooms.existsByHotelId(id)) throw ApiException.conflict("Delete hotel rooms first");
    hotels.delete(h);
  }

  public RoomView room(Long id) {
    return RoomView.of(
        rooms.findById(id).orElseThrow(() -> ApiException.missing("Room not found")));
  }

  private Room lock(Long id) {
    return rooms.lockById(id).orElseThrow(() -> ApiException.missing("Room not found"));
  }

  @Transactional
  public RoomView saveRoom(Long id, RoomInput in) {
    Room r = id == null ? new Room() : lock(id);
    if (id != null && !r.getHotel().getId().equals(in.hotelId()))
      throw ApiException.conflict("Room cannot be moved to another hotel");
    r.setHotel(findHotel(in.hotelId()));
    r.setNumber(in.number());
    r.setAvailable(in.available());
    return RoomView.of(rooms.save(r));
  }

  @Transactional
  public void deleteRoom(Long id) {
    Room r = lock(id);
    if (slots.existsByRoomId(id))
      throw ApiException.conflict("Room has booking history; set available=false instead");
    rooms.delete(r);
  }

  public List<RoomView> available(LocalDate start, LocalDate end, Long hotelId, boolean recommend) {
    dates(start, end);
    Set<Long> occupied = new HashSet<>();
    slots.occupied(start, end).forEach(s -> occupied.add(s.getRoom().getId()));
    var stream =
        rooms.findAll().stream()
            .filter(Room::isAvailable)
            .filter(r -> hotelId == null || r.getHotel().getId().equals(hotelId))
            .filter(r -> !occupied.contains(r.getId()));
    if (recommend)
      stream =
          stream.sorted(Comparator.comparingLong(Room::getTimesBooked).thenComparing(Room::getId));
    return stream.map(RoomView::of).toList();
  }

  @Transactional
  public void confirm(Long roomId, SlotInput in) {
    dates(in.startDate(), in.endDate());
    Room room = lock(roomId);
    var previous = slots.findById(in.requestId());
    if (previous.isPresent()) {
      Slot s = previous.get();
      if (!s.getRoom().getId().equals(roomId)
          || s.isReleased()
          || !in.startDate().equals(s.getStartDate())
          || !in.endDate().equals(s.getEndDate()))
        throw ApiException.conflict("requestId has been released or belongs to another request");
      return;
    }
    if (!room.isAvailable() || slots.overlaps(roomId, in.startDate(), in.endDate()))
      throw ApiException.conflict("Room unavailable for requested dates");
    slots.save(new Slot(in.requestId(), room, in.startDate(), in.endDate(), false));
    room.setTimesBooked(room.getTimesBooked() + 1);
    log.info("event=slot_confirmed bookingId={} roomId={}", in.requestId(), roomId);
  }

  @Transactional
  public void release(Long roomId, UUID requestId) {
    Room room = lock(roomId);
    var old = slots.findById(requestId);
    if (old.isPresent()) {
      Slot slot = old.get();
      if (!slot.getRoom().getId().equals(roomId))
        throw ApiException.conflict("requestId belongs to another room");
      if (!slot.isReleased()) {
        slot.setReleased(true);
        room.setTimesBooked(room.getTimesBooked() - 1);
      }
    } else slots.save(new Slot(requestId, room, null, null, true));
    log.info("event=slot_released bookingId={} roomId={}", requestId, roomId);
  }

  public List<RoomStats> stats(LocalDate start, LocalDate end, Long hotelId) {
    dates(start, end);
    var occupied = slots.occupied(start, end);
    long period = ChronoUnit.DAYS.between(start, end);
    return rooms.findAll().stream()
        .filter(r -> hotelId == null || r.getHotel().getId().equals(hotelId))
        .sorted(Comparator.comparing(Room::getId))
        .map(
            r -> {
              var matching =
                  occupied.stream().filter(s -> s.getRoom().getId().equals(r.getId())).toList();
              long days =
                  matching.stream()
                      .mapToLong(
                          s ->
                              ChronoUnit.DAYS.between(
                                  s.getStartDate().isBefore(start) ? start : s.getStartDate(),
                                  s.getEndDate().isAfter(end) ? end : s.getEndDate()))
                      .sum();
              return new RoomStats(
                  r.getId(),
                  r.getHotel().getId(),
                  r.getNumber(),
                  r.getTimesBooked(),
                  matching.size(),
                  days,
                  100.0 * days / period);
            })
        .toList();
  }
}
