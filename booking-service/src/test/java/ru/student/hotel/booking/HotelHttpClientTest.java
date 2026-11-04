package ru.student.hotel.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import ru.student.hotel.common.ApiException;

class HotelHttpClientTest {
  HttpServer server;
  ExecutorService executor;
  AtomicInteger calls;
  HotelHttpClient client;
  UUID id;
  LocalDate start = LocalDate.now().plusDays(1);

  @BeforeEach
  void setup() throws Exception {
    calls = new AtomicInteger();
    id = UUID.randomUUID();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.start();
    var tokens = mock(TokenService.class);
    when(tokens.internal()).thenReturn("internal-token");
    client =
        new HotelHttpClient(
            RestClient.builder(),
            tokens,
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "key",
            80,
            3,
            5);
  }

  @AfterEach
  void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  void respond(HttpExchange e, int status) throws IOException {
    e.sendResponseHeaders(status, -1);
    e.close();
  }

  @Test
  void retriesServerErrorsAndPreservesCorrelationAndIdempotencyKey() {
    List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    server.createContext(
        "/api/rooms/1/confirm-availability",
        e -> {
          assertThat(e.getRequestHeaders().getFirst("X-Request-Id")).isEqualTo(id.toString());
          assertThat(e.getRequestHeaders().getFirst("Authorization"))
              .isEqualTo("Bearer internal-token");
          bodies.add(
              new String(
                  e.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
          respond(e, calls.incrementAndGet() < 3 ? 503 : 200);
        });
    client.confirm(1L, start, start.plusDays(1), id);
    assertThat(calls.get()).isEqualTo(3);
    assertThat(bodies).hasSize(3).allMatch(b -> b.equals(bodies.get(0)));
  }

  @Test
  void doesNotRetryConflict() {
    server.createContext(
        "/api/rooms/1/confirm-availability",
        e -> {
          calls.incrementAndGet();
          respond(e, 409);
        });
    assertThatThrownBy(() -> client.confirm(1L, start, start.plusDays(1), id))
        .isInstanceOf(ApiException.class);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void timeoutHasBoundedRetries() {
    server.createContext(
        "/api/rooms/1/confirm-availability",
        e -> {
          calls.incrementAndGet();
          try {
            Thread.sleep(500);
          } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
          }
          try {
            respond(e, 200);
          } catch (IOException ignored) {
          }
        });
    long before = System.nanoTime();
    assertThatThrownBy(() -> client.confirm(1L, start, start.plusDays(1), id))
        .isInstanceOf(HotelUnavailableException.class);
    assertThat(calls.get()).isEqualTo(3);
    assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void circuitBreakerOpensButCompensationStillCallsHotel() {
    server.createContext(
        "/api/rooms/1/confirm-availability",
        e -> {
          calls.incrementAndGet();
          respond(e, 503);
        });
    server.createContext(
        "/api/rooms/1/release",
        e -> {
          calls.incrementAndGet();
          respond(e, 200);
        });
    assertThatThrownBy(() -> client.confirm(1L, start, start.plusDays(1), id))
        .isInstanceOf(HotelUnavailableException.class);
    assertThat(calls.get()).isEqualTo(3);
    assertThatThrownBy(() -> client.confirm(1L, start, start.plusDays(1), id))
        .isInstanceOf(HotelUnavailableException.class);
    assertThat(calls.get()).isEqualTo(3);
    client.release(1L, id);
    assertThat(calls.get()).isEqualTo(4);
  }
}
