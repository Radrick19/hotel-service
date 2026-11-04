package ru.student.hotel.gateway;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
class GatewayIntegrationTest {
  @Autowired WebTestClient web;
  @Autowired RouteLocator routes;

  String token() {
    var key =
        new SecretKeySpec(
            "student-hotel-demo-secret-at-least-32-characters".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");
    var encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .issuer("hotel-booking")
                    .subject("1")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .claim("roles", List.of("USER"))
                    .build()))
        .getTokenValue();
  }

  @Test
  void requiresValidTokenAndAddsTrace() {
    web.get()
        .uri("/api/hotels")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .exists("X-Request-Id")
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401);
    web.get()
        .uri("/api/hotels")
        .headers(h -> h.setBearerAuth("bad-token"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void internalOperationsAreNotPublished() {
    web.post()
        .uri("/api/rooms/1/release")
        .headers(h -> h.setBearerAuth(token()))
        .exchange()
        .expectStatus()
        .isForbidden();
    web.post()
        .uri("/api/rooms/1/confirm-availability")
        .headers(h -> h.setBearerAuth(token()))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void dynamicRoutesUseEurekaNames() {
    var list = routes.getRoutes().collectList().block();
    assertThat(list).hasSize(2);
    assertThat(list)
        .extracting(r -> r.getUri().toString())
        .containsExactlyInAnyOrder("lb://hotel-service", "lb://booking-service");
  }

  @Test
  void validatesJwtBeforeRoutingAndPreservesTrace() {
    web.get()
        .uri("/nonexistent")
        .headers(
            h -> {
              h.setBearerAuth(token());
              h.set("X-Request-Id", "test-trace");
            })
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .valueEquals("X-Request-Id", "test-trace");
  }
}
