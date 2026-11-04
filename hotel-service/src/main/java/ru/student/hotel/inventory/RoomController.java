package ru.student.hotel.inventory;

import static ru.student.hotel.inventory.InventoryDtos.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
  private final InventoryService service;

  @Value("${security.internal-key}")
  private String internalKey;

  @GetMapping
  @PreAuthorize("hasAnyRole('USER','ADMIN','INTERNAL')")
  public List<RoomView> list(
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(required = false) Long hotelId) {
    return service.available(startDate, endDate, hotelId, false);
  }

  @GetMapping("/recommend")
  @PreAuthorize("hasAnyRole('USER','ADMIN','INTERNAL')")
  public List<RoomView> recommend(
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(required = false) Long hotelId) {
    return service.available(startDate, endDate, hotelId, true);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('USER','ADMIN')")
  public RoomView get(@PathVariable Long id) {
    return service.room(id);
  }

  @GetMapping("/statistics")
  @PreAuthorize("hasRole('ADMIN')")
  public List<RoomStats> stats(
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(required = false) Long hotelId) {
    return service.stats(startDate, endDate, hotelId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView create(@Valid @RequestBody RoomInput in) {
    return service.saveRoom(null, in);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView update(@PathVariable Long id, @Valid @RequestBody RoomInput in) {
    return service.saveRoom(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    service.deleteRoom(id);
  }

  @PostMapping("/{id}/confirm-availability")
  @PreAuthorize("hasRole('INTERNAL')")
  public void confirm(
      @PathVariable Long id,
      @Valid @RequestBody SlotInput in,
      @RequestHeader("X-Internal-Key") String key) {
    checkKey(key);
    service.confirm(id, in);
  }

  @PostMapping("/{id}/release")
  @PreAuthorize("hasRole('INTERNAL')")
  public void release(
      @PathVariable Long id,
      @Valid @RequestBody ReleaseInput in,
      @RequestHeader("X-Internal-Key") String key) {
    checkKey(key);
    service.release(id, in.requestId());
  }

  private void checkKey(String key) {
    if (!java.security.MessageDigest.isEqual(
        internalKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        key.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
      throw new AccessDeniedException("Internal key required");
  }
}
