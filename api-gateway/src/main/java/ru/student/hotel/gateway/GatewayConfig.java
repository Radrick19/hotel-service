package ru.student.hotel.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {
  @Bean
  ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
    var d =
        NimbusReactiveJwtDecoder.withSecretKey(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    d.setJwtValidator(JwtValidators.createDefaultWithIssuer("hotel-booking"));
    return d;
  }

  @Bean
  SecurityWebFilterChain security(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            a ->
                a.pathMatchers("/user/auth", "/user/register")
                    .permitAll()
                    .pathMatchers("/api/rooms/*/release", "/api/rooms/*/confirm-availability")
                    .denyAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> {})
                    .authenticationEntryPoint((e, x) -> error(e, 401, "Authentication required")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((x, y) -> error(x, 401, "Authentication required"))
                    .accessDeniedHandler((x, y) -> error(x, 403, "Access denied")))
        .build();
  }

  private Mono<Void> error(
      org.springframework.web.server.ServerWebExchange e, int status, String message) {
    e.getResponse().setStatusCode(HttpStatus.valueOf(status));
    e.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String json =
        "{\"timestamp\":\""
            + Instant.now()
            + "\",\"status\":"
            + status
            + ",\"message\":\""
            + message
            + "\",\"traceId\":\""
            + e.getResponse().getHeaders().getFirst("X-Request-Id")
            + "\"}";
    return e.getResponse()
        .writeWith(
            Mono.just(e.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8))));
  }

  @Bean
  RouteLocator routes(RouteLocatorBuilder builder) {
    return builder
        .routes()
        .route(
            "booking",
            r ->
                r.path(
                        "/api/bookings",
                        "/api/bookings/**",
                        "/booking",
                        "/booking/**",
                        "/bookings",
                        "/user",
                        "/user/**")
                    .filters(f -> f.removeRequestHeader("X-Internal-Key"))
                    .uri("lb://booking-service"))
        .route(
            "hotel",
            r ->
                r.path("/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**")
                    .and()
                    .not(p -> p.path("/api/rooms/*/release", "/api/rooms/*/confirm-availability"))
                    .filters(f -> f.removeRequestHeader("X-Internal-Key"))
                    .uri("lb://hotel-service"))
        .build();
  }
}
