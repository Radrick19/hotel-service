package ru.student.hotel.inventory;

import static ru.student.hotel.inventory.InventoryDtos.*;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {
  private final InventoryService service;

  @GetMapping
  @PreAuthorize("hasAnyRole('USER','ADMIN')")
  public List<HotelView> list(@RequestParam(required = false) String search) {
    return service.hotels(search);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('USER','ADMIN')")
  public HotelView get(@PathVariable Long id) {
    return service.hotel(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public HotelView create(@Valid @RequestBody HotelInput in) {
    return service.saveHotel(null, in);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelView update(@PathVariable Long id, @Valid @RequestBody HotelInput in) {
    return service.saveHotel(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable Long id) {
    service.deleteHotel(id);
  }
}
