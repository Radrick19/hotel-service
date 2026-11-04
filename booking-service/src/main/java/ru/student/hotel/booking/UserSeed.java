package ru.student.hotel.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserSeed implements CommandLineRunner {
  private final BookingRepository bookings;
  private final UserRepository users;
  private final PasswordEncoder passwords;

  @Override
  @Transactional
  public void run(String... args) {
    if (users.count() > 0) return;
    users.save(new User("admin", passwords.encode("admin12345"), User.Role.ADMIN));
    var student = users.save(new User("student", passwords.encode("student12345"), User.Role.USER));
    for (int i = 1; i <= 3; i++) {
      var b = new Booking();
      b.setId(ru.student.hotel.common.DemoData.bookingId(i));
      b.setUser(student);
      b.setRoomId(ru.student.hotel.common.DemoData.roomId(i));
      b.setRequestedRoomId(b.getRoomId());
      b.setStartDate(ru.student.hotel.common.DemoData.start(i));
      b.setEndDate(b.getStartDate().plusDays(2));
      b.setStatus(Booking.Status.CONFIRMED);
      bookings.save(b);
    }
  }
}
