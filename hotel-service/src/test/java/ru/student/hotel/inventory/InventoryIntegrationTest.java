package ru.student.hotel.inventory;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.student.hotel.inventory.InventoryDtos.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.student.hotel.common.ApiException;

@SpringBootTest(
    properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc
class InventoryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired InventoryService service;
  @Autowired HotelRepository hotels;
  @Autowired RoomRepository rooms;
  @Autowired SlotRepository slots;
  Long hotelId, roomId, secondId;
  LocalDate start = LocalDate.now().plusDays(10), end = start.plusDays(3);

  @BeforeEach
  void setup() {
    slots.deleteAll();
    rooms.deleteAll();
    hotels.deleteAll();
    var h = hotels.save(new Hotel("Test Hotel", "Test Address"));
    hotelId = h.getId();
    roomId = rooms.save(new Room(h, "101")).getId();
    secondId = rooms.save(new Room(h, "102")).getId();
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Test
  void requiresAuthenticationAndAdmin() throws Exception {
    mvc.perform(get("/api/hotels"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    mvc.perform(
            post("/api/hotels")
                .with(role("USER"))
                .contentType("application/json")
                .content("{\"name\":\"A\",\"address\":\"B\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void crudHotelsAndRoomsAndValidation() throws Exception {
    mvc.perform(
            post("/api/hotels")
                .with(role("ADMIN"))
                .contentType("application/json")
                .content("{\"name\":\"New\",\"address\":\"Address\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            put("/api/hotels/" + hotelId)
                .with(role("ADMIN"))
                .contentType("application/json")
                .content("{\"name\":\"Updated\",\"address\":\"Address\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"));
    mvc.perform(
            post("/api/rooms")
                .with(role("ADMIN"))
                .contentType("application/json")
                .content("{\"hotelId\":" + hotelId + ",\"number\":\"103\",\"available\":true}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/hotels")
                .with(role("ADMIN"))
                .contentType("application/json")
                .content("{\"name\":\"\",\"address\":\"B\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/hotels/" + hotelId).with(role("ADMIN")))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/rooms/" + secondId).with(role("ADMIN")))
        .andExpect(status().isNoContent());
  }

  @Test
  void sortsByCounterThenIdAndUsesDateIntervals() throws Exception {
    service.confirm(roomId, new SlotInput(UUID.randomUUID(), start, end));
    mvc.perform(
            get("/api/rooms/recommend")
                .with(role("USER"))
                .param("startDate", end.toString())
                .param("endDate", end.plusDays(2).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(secondId))
        .andExpect(jsonPath("$[1].id").value(roomId));
    mvc.perform(
            get("/api/rooms")
                .with(role("USER"))
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(
            get("/api/rooms")
                .with(role("USER"))
                .param("startDate", end.toString())
                .param("endDate", start.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void excludesMaintenanceRoomsAndFiltersHotels() {
    var r = rooms.findById(roomId).orElseThrow();
    r.setAvailable(false);
    rooms.save(r);
    assertThat(service.available(start, end, hotelId, false))
        .extracting(RoomView::id)
        .containsExactly(secondId);
    assertThat(service.available(start, end, 999L, false)).isEmpty();
  }

  @Test
  void releaseIsIdempotentAndLateConfirmCannotReacquire() {
    var id = UUID.randomUUID();
    service.release(roomId, id);
    assertThatThrownBy(() -> service.confirm(roomId, new SlotInput(id, start, end)))
        .isInstanceOf(ApiException.class);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isZero();
  }

  @Test
  void confirmAndReleaseAreIdempotent() {
    var id = UUID.randomUUID();
    var in = new SlotInput(id, start, end);
    service.confirm(roomId, in);
    service.confirm(roomId, in);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
    service.release(roomId, id);
    service.release(roomId, id);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isZero();
    assertThat(service.available(start, end, null, false)).hasSize(2);
  }

  @Test
  void oneWinnerForConcurrentOverlappingRequests() throws Exception {
    AtomicInteger wins = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    var pool = Executors.newFixedThreadPool(12);
    var gate = new CountDownLatch(1);
    try {
      List<Future<?>> tasks = new ArrayList<>();
      for (int i = 0; i < 12; i++)
        tasks.add(
            pool.submit(
                () -> {
                  try {
                    gate.await();
                    service.confirm(roomId, new SlotInput(UUID.randomUUID(), start, end));
                    wins.incrementAndGet();
                  } catch (ApiException e) {
                    conflicts.incrementAndGet();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                }));
      gate.countDown();
      for (var t : tasks) t.get(20, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    assertThat(wins.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(11);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void concurrentDuplicateDeliveryChangesCounterOnce() throws Exception {
    var id = UUID.randomUUID();
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Void>> calls = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        calls.add(
            () -> {
              service.confirm(roomId, new SlotInput(id, start, end));
              return null;
            });
      for (var f : pool.invokeAll(calls)) f.get();
    } finally {
      pool.shutdownNow();
    }
    assertThat(slots.count()).isEqualTo(1);
    assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
  }

  @Test
  void internalEndpointsRejectUserAndRequireInternalKey() throws Exception {
    String body =
        "{\"requestId\":\""
            + UUID.randomUUID()
            + "\",\"startDate\":\""
            + start
            + "\",\"endDate\":\""
            + end
            + "\"}";
    mvc.perform(
            post("/api/rooms/" + roomId + "/confirm-availability")
                .with(role("USER"))
                .header("X-Internal-Key", "student-internal-demo-key")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/rooms/" + roomId + "/confirm-availability")
                .with(role("INTERNAL"))
                .header("X-Internal-Key", "wrong")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/rooms/" + roomId + "/confirm-availability")
                .with(role("INTERNAL"))
                .header("X-Internal-Key", "student-internal-demo-key")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void statisticsClipsDatesAndCountsOccupancy() throws Exception {
    service.confirm(roomId, new SlotInput(UUID.randomUUID(), start, end));
    mvc.perform(
            get("/api/rooms/statistics")
                .with(role("ADMIN"))
                .param("startDate", start.toString())
                .param("endDate", start.plusDays(6).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].occupiedDays").value(3))
        .andExpect(jsonPath("$[0].occupancyPercent").value(50.0));
    mvc.perform(
            get("/api/rooms/statistics")
                .with(role("USER"))
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void swaggerIsAvailable() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/hotels']").exists());
  }

  @Test
  void acceptsAddressUpToDeclaredValidationLimit() throws Exception {
    mvc.perform(
            post("/api/hotels")
                .with(role("ADMIN"))
                .contentType("application/json")
                .content("{\"name\":\"Hotel\",\"address\":\"" + "a".repeat(500) + "\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/hotels")
                .with(role("ADMIN"))
                .contentType("application/json")
                .content("{\"name\":\"Hotel\",\"address\":\"" + "a".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest());
  }
}
