package ru.student.hotel.booking;

import static ru.student.hotel.booking.BookingDtos.*;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {
  private final JwtEncoder encoder;

  public Token user(User u) {
    return new Token(encode(u.getId().toString(), u.getRole().name(), 3600), "Bearer", 3600);
  }

  public String internal() {
    return encode("booking-service", "INTERNAL", 60);
  }

  private String encode(String subject, String role, long seconds) {
    Instant now = Instant.now();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .issuer("hotel-booking")
                    .subject(subject)
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(seconds))
                    .claim("roles", List.of(role))
                    .build()))
        .getTokenValue();
  }
}
