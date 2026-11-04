package ru.student.hotel.booking;

import static ru.student.hotel.booking.BookingDtos.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.student.hotel.common.ApiException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
  private final UserRepository users;
  private final BookingRepository bookings;
  private final PasswordEncoder passwords;
  private final TokenService tokens;

  @Transactional
  public Token register(Credentials in) {
    return tokens.user(create(new UserInput(in.username(), in.password(), User.Role.USER)));
  }

  public Token login(Credentials in) {
    var u =
        users
            .findByUsername(in.username())
            .filter(x -> passwords.matches(in.password(), x.getPassword()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    return tokens.user(u);
  }

  @Transactional
  public User create(UserInput in) {
    if (users.findByUsername(in.username()).isPresent())
      throw ApiException.conflict("Username already exists");
    return users.save(new User(in.username(), passwords.encode(in.password()), in.role()));
  }

  public UserView get(Long id) {
    return UserView.of(find(id));
  }

  public java.util.List<UserView> list() {
    return users.findAll().stream().map(UserView::of).toList();
  }

  private User find(Long id) {
    return users.findById(id).orElseThrow(() -> ApiException.missing("User not found"));
  }

  @Transactional
  public UserView update(UserUpdate in) {
    User u = find(in.id());
    u.setUsername(in.username());
    u.setPassword(passwords.encode(in.password()));
    u.setRole(in.role());
    return UserView.of(u);
  }

  @Transactional
  public void delete(Long id) {
    User u = find(id);
    if (bookings.existsByUserId(id)) throw ApiException.conflict("User has booking history");
    users.delete(u);
  }
}
