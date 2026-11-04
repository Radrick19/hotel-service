package ru.student.hotel.booking;

import io.github.resilience4j.circuitbreaker.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import ru.student.hotel.common.ApiException;

@Component
@Slf4j
public class HotelHttpClient implements HotelClient {
  private final RestClient client;
  private final TokenService tokens;
  private final String key;
  private final int attempts;
  private final long delay;
  private final CircuitBreaker breaker =
      CircuitBreaker.of(
          "hotel",
          CircuitBreakerConfig.custom()
              .slidingWindowSize(6)
              .minimumNumberOfCalls(3)
              .failureRateThreshold(50)
              .waitDurationInOpenState(Duration.ofSeconds(5))
              .ignoreExceptions(ApiException.class)
              .build());

  public HotelHttpClient(
      @LoadBalanced RestClient.Builder builder,
      TokenService tokens,
      @Value("${hotel.base-url}") String url,
      @Value("${security.internal-key}") String key,
      @Value("${hotel.timeout-ms}") int timeout,
      @Value("${hotel.max-attempts}") int attempts,
      @Value("${hotel.retry-delay-ms}") long delay) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    this.client = builder.requestFactory(factory).baseUrl(url).build();
    this.tokens = tokens;
    this.key = key;
    this.attempts = attempts;
    this.delay = delay;
  }

  @Configuration
  static class ClientConfiguration {
    @Bean
    @LoadBalanced
    RestClient.Builder hotelRestBuilder() {
      return RestClient.builder();
    }
  }

  @Override
  public List<BookingDtos.RoomView> recommend(
      LocalDate start, LocalDate end, Long hotelId, UUID id) {
    return call(
        id,
        false,
        () ->
            client
                .get()
                .uri(
                    u -> {
                      var b =
                          u.path("/api/rooms/recommend")
                              .queryParam("startDate", start)
                              .queryParam("endDate", end);
                      if (hotelId != null) b.queryParam("hotelId", hotelId);
                      return b.build();
                    })
                .headers(h -> headers(h, id))
                .retrieve()
                .body(new ParameterizedTypeReference<List<BookingDtos.RoomView>>() {}));
  }

  @Override
  public void confirm(Long roomId, LocalDate start, LocalDate end, UUID id) {
    call(
        id,
        false,
        () -> {
          client
              .post()
              .uri("/api/rooms/{id}/confirm-availability", roomId)
              .headers(h -> headers(h, id))
              .body(
                  Map.of(
                      "requestId",
                      HotelClient.slotId(id, roomId),
                      "startDate",
                      start,
                      "endDate",
                      end))
              .retrieve()
              .toBodilessEntity();
          return null;
        });
  }

  @Override
  public void release(Long roomId, UUID id) {
    try {
      call(
          id,
          true,
          () -> {
            client
                .post()
                .uri("/api/rooms/{id}/release", roomId)
                .headers(h -> headers(h, id))
                .body(Map.of("requestId", HotelClient.slotId(id, roomId)))
                .retrieve()
                .toBodilessEntity();
            return null;
          });
    } catch (ApiException e) {
      if (e.status().value() != 404) throw e;
    }
  }

  private void headers(org.springframework.http.HttpHeaders h, UUID id) {
    h.setBearerAuth(tokens.internal());
    h.set("X-Internal-Key", key);
    h.set("X-Request-Id", id.toString());
  }

  private <T> T call(UUID id, boolean compensation, Supplier<T> action) {
    for (int attempt = 1; attempt <= attempts; attempt++) {
      try {
        Supplier<T> translated =
            () -> {
              try {
                return action.get();
              } catch (RestClientResponseException e) {
                if (e.getStatusCode().is4xxClientError())
                  throw new ApiException(
                      org.springframework.http.HttpStatus.valueOf(e.getStatusCode().value()),
                      "Hotel rejected request");
                throw e;
              }
            };
        return compensation ? translated.get() : breaker.executeSupplier(translated);
      } catch (ApiException e) {
        throw e;
      } catch (Exception e) {
        log.warn(
            "event=hotel_call_failed bookingId={} attempt={} compensation={} cause={}",
            id,
            attempt,
            compensation,
            e.getClass().getSimpleName());
        if (attempt == attempts || e instanceof CallNotPermittedException)
          throw new HotelUnavailableException("Hotel service unavailable", e);
        try {
          Thread.sleep(delay * (1L << (attempt - 1)));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new HotelUnavailableException("Hotel call interrupted", interrupted);
        }
      }
    }
    throw new IllegalStateException("No attempts configured");
  }
}
