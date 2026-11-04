package ru.student.hotel.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.student.hotel.booking.BookingDtos.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.student.hotel.common.ApiException;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "booking.compensation-delay-ms=999999"
    })
@AutoConfigureMockMvc
class BookingIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired BookingService service;
  @Autowired BookingStore store;
  @Autowired BookingSaga saga;
  @Autowired BookingRepository bookings;
  @Autowired UserRepository users;
  @Autowired UserService userService;
  @Autowired TokenService tokens;
  @Autowired CompensationRecovery recovery;
  @MockitoBean HotelClient hotel;
  User user, other, admin;
  String token, adminToken;
  LocalDate start = LocalDate.now().plusDays(10), end = start.plusDays(3);

  @BeforeEach
  void setup() {
    bookings.deleteAll();
    users.deleteAll();
    reset(hotel);
    user = userService.create(new UserInput("student", "password123", User.Role.USER));
    other = userService.create(new UserInput("another", "password123", User.Role.USER));
    admin = userService.create(new UserInput("admin", "password123", User.Role.ADMIN));
    token = tokens.user(user).token();
    adminToken = tokens.user(admin).token();
  }

  BookingInput input(UUID id) {
    return new BookingInput(id, 1L, null, false, start, end);
  }

  String json(UUID id) {
    return "{\"requestId\":\""
        + id
        + "\",\"roomId\":1,\"autoSelect\":false,\"startDate\":\""
        + start
        + "\",\"endDate\":\""
        + end
        + "\"}";
  }

  @Test
  void successCommitsPendingBeforeRemoteCallAndConfirms() throws Exception {
    var id = UUID.randomUUID();
    doAnswer(
            call -> {
              var pending = bookings.findById(id).orElseThrow();
              assertThat(pending.getStatus()).isEqualTo(Booking.Status.PENDING);
              assertThat(pending.getRoomId()).isEqualTo(1L);
              return null;
            })
        .when(hotel)
        .confirm(anyLong(), any(), any(), any());
    mvc.perform(
            post("/booking")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json(id)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(bookings.count()).isEqualTo(1);
    verify(hotel).confirm(1L, start, end, id);
  }

  @Test
  void replayReturnsSameBookingAndDifferentPayloadConflicts() throws Exception {
    var id = UUID.randomUUID();
    var first = service.create(user.getId(), input(id));
    var second = service.create(user.getId(), input(id));
    assertThat(second).isEqualTo(first);
    assertThat(bookings.count()).isEqualTo(1);
    verify(hotel, times(1)).confirm(anyLong(), any(), any(), any());
    mvc.perform(
            post("/booking")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json(id).replace("\"roomId\":1", "\"roomId\":2")))
        .andExpect(status().isConflict());
  }

  @Test
  void remoteErrorCancelsAndCompensates() throws Exception {
    doThrow(new HotelUnavailableException("Down", new IllegalStateException()))
        .when(hotel)
        .confirm(anyLong(), any(), any(), any());
    var id = UUID.randomUUID();
    mvc.perform(
            post("/booking")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json(id)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.releasePending").value(false));
    verify(hotel).release(1L, id);
  }

  @Test
  void timeoutCancelsAndFailedCompensationIsRetried() {
    var id = UUID.randomUUID();
    doThrow(new HotelUnavailableException("Timeout", new java.net.SocketTimeoutException()))
        .when(hotel)
        .confirm(anyLong(), any(), any(), any());
    doThrow(new HotelUnavailableException("Still down", null))
        .when(hotel)
        .release(anyLong(), any());
    assertThat(service.create(user.getId(), input(id)).releasePending()).isTrue();
    doNothing().when(hotel).release(anyLong(), any());
    recovery.recover();
    assertThat(store.get(id, user.getId()).releasePending()).isFalse();
    verify(hotel, times(2)).release(1L, id);
  }

  @Test
  void conflictIsCancelled() {
    doThrow(ApiException.conflict("Occupied")).when(hotel).confirm(anyLong(), any(), any(), any());
    assertThat(service.create(user.getId(), input(UUID.randomUUID())).status())
        .isEqualTo(Booking.Status.CANCELLED);
  }

  @Test
  void autoSelectIgnoresRoomIdAndRetriesConflictingCandidate() {
    var id = UUID.randomUUID();
    when(hotel.recommend(start, end, null, id))
        .thenReturn(List.of(new RoomView(2L, 1L, "102", true, 0)))
        .thenReturn(List.of(new RoomView(3L, 1L, "103", true, 0)));
    doThrow(ApiException.conflict("Race")).when(hotel).confirm(2L, start, end, id);
    var b = service.create(user.getId(), new BookingInput(id, 999L, null, true, start, end));
    assertThat(b.status()).isEqualTo(Booking.Status.CONFIRMED);
    assertThat(b.roomId()).isEqualTo(3L);
    verify(hotel).release(2L, id);
  }

  @Test
  void emptyRecommendationCancels() {
    var id = UUID.randomUUID();
    when(hotel.recommend(start, end, null, id)).thenReturn(List.of());
    assertThat(
            service
                .create(user.getId(), new BookingInput(id, null, null, true, start, end))
                .status())
        .isEqualTo(Booking.Status.CANCELLED);
    verify(hotel, never()).confirm(anyLong(), any(), any(), any());
  }

  @Test
  void ownershipHistoryAndCancellation() throws Exception {
    var id = UUID.randomUUID();
    service.create(user.getId(), input(id));
    var otherToken = tokens.user(other).token();
    mvc.perform(get("/booking/" + id).header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/booking/" + id).header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
    mvc.perform(get("/bookings").header("Authorization", "Bearer " + otherToken))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/bookings").header("Authorization", "Bearer " + token).param("size", "1"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id.toString()));
    mvc.perform(delete("/booking/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    verify(hotel).release(1L, id);
  }

  @Test
  void validatesDatesPaginationAndRequiredRoom() throws Exception {
    var id = UUID.randomUUID();
    mvc.perform(
            post("/booking")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json(id).replace(end.toString(), start.toString())))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/booking")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json(id).replace("\"roomId\":1,", "")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/bookings").header("Authorization", "Bearer " + token).param("size", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/bookings").header("Authorization", "Bearer " + token).param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void authenticationRegistrationAndRoles() throws Exception {
    mvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
    mvc.perform(get("/bookings").header("Authorization", "Bearer broken"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/user").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/user").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    mvc.perform(
            post("/user/register")
                .contentType("application/json")
                .content(
                    "{\"username\":\"registered\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.expiresIn").value(3600));
    assertThat(users.findByUsername("registered").orElseThrow().getRole())
        .isEqualTo(User.Role.USER);
    mvc.perform(
            post("/user/auth")
                .contentType("application/json")
                .content("{\"username\":\"student\",\"password\":\"password123\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/user/auth")
                .contentType("application/json")
                .content("{\"username\":\"student\",\"password\":\"incorrect\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminUserCrudAndPasswordHashing() throws Exception {
    mvc.perform(
            post("/user")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    "{\"username\":\"newuser\",\"password\":\"password123\",\"role\":\"USER\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").doesNotExist());
    User u = users.findByUsername("newuser").orElseThrow();
    assertThat(u.getPassword()).startsWith("$2");
    mvc.perform(
            patch("/user")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(
                    "{\"id\":"
                        + u.getId()
                        + ",\"username\":\"renamed\",\"password\":\"password456\",\"role\":\"USER\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            delete("/user")
                .header("Authorization", "Bearer " + adminToken)
                .param("id", u.getId().toString()))
        .andExpect(status().isNoContent());
  }

  @Test
  void parallelDuplicateDeliveryCreatesOneBooking() throws Exception {
    var id = UUID.randomUUID();
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<BookingView>> calls = new ArrayList<>();
      for (int i = 0; i < 12; i++) calls.add(() -> service.create(user.getId(), input(id)));
      for (var f : pool.invokeAll(calls))
        assertThat(f.get(20, TimeUnit.SECONDS).status()).isEqualTo(Booking.Status.CONFIRMED);
    } finally {
      pool.shutdownNow();
    }
    assertThat(bookings.count()).isEqualTo(1);
    verify(hotel, times(1)).confirm(anyLong(), any(), any(), any());
  }

  @Test
  void recoveryResumesCommittedPendingRoom() {
    var id = UUID.randomUUID();
    store.create(user.getId(), input(id));
    saga.prepare(id);
    assertThat(saga.process(id).status()).isEqualTo(Booking.Status.CONFIRMED);
    verify(hotel).confirm(1L, start, end, id);
  }

  @Test
  void swaggerIsAvailable() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/booking']").exists());
  }
}
