package ru.student.hotel.booking;

import static ru.student.hotel.booking.BookingDtos.*;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {
  private final UserService users;

  @PostMapping("/user/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Token register(@Valid @RequestBody Credentials in) {
    return users.register(in);
  }

  @PostMapping("/user/auth")
  public Token login(@Valid @RequestBody Credentials in) {
    return users.login(in);
  }

  @PostMapping("/user")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public UserView create(@Valid @RequestBody UserInput in) {
    return UserView.of(users.create(in));
  }

  @GetMapping("/user")
  @PreAuthorize("hasRole('ADMIN')")
  public List<UserView> list() {
    return users.list();
  }

  @GetMapping("/user/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public UserView get(@PathVariable Long id) {
    return users.get(id);
  }

  @PatchMapping("/user")
  @PreAuthorize("hasRole('ADMIN')")
  public UserView update(@Valid @RequestBody UserUpdate in) {
    return users.update(in);
  }

  @DeleteMapping("/user")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@RequestParam Long id) {
    users.delete(id);
  }
}
