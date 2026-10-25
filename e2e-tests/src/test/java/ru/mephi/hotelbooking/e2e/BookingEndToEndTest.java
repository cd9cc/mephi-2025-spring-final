package ru.mephi.hotelbooking.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.mephi.hotelbooking.booking.BookingServiceApplication;
import ru.mephi.hotelbooking.hotel.HotelServiceApplication;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingEndToEndTest {

    private static final RestClient HTTP = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();

    private static ConfigurableApplicationContext hotelService;
    private static ConfigurableApplicationContext bookingService;
    private static String hotelUrl;
    private static String bookingUrl;

    @BeforeAll
    static void startServices() {
        hotelService = start(HotelServiceApplication.class, "hotel-service");
        hotelUrl = baseUrl(hotelService);
        bookingService = start(BookingServiceApplication.class, "booking-service",
                "--spring.cloud.discovery.client.simple.instances.hotel-service[0].uri=" + hotelUrl);
        bookingUrl = baseUrl(bookingService);
    }

    @AfterAll
    static void stopServices() {
        bookingService.close();
        hotelService.close();
    }

    @Test
    void seededBookingOccupiesRoomUntilUserCancelsIt() {
        String token = login("user", "user123");
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(2);
        assertThat(freeRoomIds(token, start, end)).doesNotContain(1L);

        assertThat(book(token, "e2e-busy", 1L, start, end).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> cancelled = call(HttpMethod.DELETE, bookingUrl + "/api/bookings/booking/1", token);
        assertThat(cancelled.getBody().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(freeRoomIds(token, start, end)).contains(1L);

        ResponseEntity<JsonNode> rebooked = book(token, "e2e-rebook", 1L, start, end);
        assertThat(rebooked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rebooked.getBody().get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void autoSelectBooksLeastBookedFreeRoom() {
        String token = login("alice", "alice123");
        LocalDate start = LocalDate.of(2031, 3, 1);
        LocalDate end = start.plusDays(2);
        JsonNode leastBooked = recommend(token, start, end).getFirst();

        ResponseEntity<JsonNode> response = book(token, "e2e-auto", null, start, end);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long roomId = response.getBody().get("roomId").asLong();
        assertThat(roomId).isEqualTo(leastBooked.get("id").asLong());
        assertThat(timesBooked(token, roomId)).isEqualTo(leastBooked.get("timesBooked").asLong() + 1);
    }

    @Test
    void concurrentBookingsOfOneRoomConfirmExactlyOne() throws Exception {
        List<String> tokens = List.of(login("user", "user123"), login("alice", "alice123"));
        LocalDate start = LocalDate.of(2031, 4, 1);
        LocalDate end = start.plusDays(4);
        int attempts = 8;
        List<Callable<HttpStatus>> tasks = IntStream.range(0, attempts)
                .<Callable<HttpStatus>>mapToObj(i -> () -> HttpStatus.valueOf(
                        book(tokens.get(i % 2), "e2e-race-" + i, 7L, start, end).getStatusCode().value()))
                .toList();

        List<HttpStatus> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            statuses = executor.invokeAll(tasks).stream().map(BookingEndToEndTest::await).toList();
        }

        assertThat(statuses).containsOnlyOnce(HttpStatus.CREATED);
        assertThat(statuses).filteredOn(status -> status != HttpStatus.CREATED).containsOnly(HttpStatus.CONFLICT);
        assertThat(freeRoomIds(tokens.getFirst(), start, end)).doesNotContain(7L);
    }

    @Test
    void repeatedRequestIsProcessedOnce() {
        String token = login("alice", "alice123");
        LocalDate start = LocalDate.of(2031, 5, 1);
        LocalDate end = start.plusDays(2);
        long timesBookedBefore = timesBooked(token, 8L);

        ResponseEntity<JsonNode> first = book(token, "e2e-retry", 8L, start, end);
        ResponseEntity<JsonNode> repeated = book(token, "e2e-retry", 8L, start, end);

        assertThat(repeated.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(timesBooked(token, 8L)).isEqualTo(timesBookedBefore + 1);
    }

    @Test
    @Order(Integer.MAX_VALUE)
    void hotelOutageCancelsBooking() {
        String token = login("user", "user123");
        hotelService.close();

        ResponseEntity<JsonNode> response = book(token, "e2e-outage", 9L, LocalDate.of(2031, 6, 1),
                LocalDate.of(2031, 6, 3));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode booking = historyEntry(token, "e2e-outage");
        assertThat(booking.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(booking.get("cancelReason").asText()).isEqualTo("HOTEL_UNAVAILABLE");
    }

    private static String login(String username, String password) {
        Map<String, String> credentials = Map.of("username", username, "password", password);
        return call(HttpMethod.POST, bookingUrl + "/api/bookings/user/auth", null, credentials)
                .getBody().get("accessToken").asText();
    }

    private static ResponseEntity<JsonNode> book(String token, String requestId, Long roomId,
                                                 LocalDate start, LocalDate end) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", requestId);
        request.put("roomId", roomId);
        request.put("autoSelect", roomId == null);
        request.put("startDate", start.toString());
        request.put("endDate", end.toString());
        return call(HttpMethod.POST, bookingUrl + "/api/bookings/booking", token, request);
    }

    private static List<Long> freeRoomIds(String token, LocalDate start, LocalDate end) {
        JsonNode rooms = call(HttpMethod.GET, hotelUrl + "/api/rooms?startDate=%s&endDate=%s".formatted(start, end),
                token).getBody();
        return StreamSupport.stream(rooms.spliterator(), false).map(room -> room.get("id").asLong()).toList();
    }

    private static List<JsonNode> recommend(String token, LocalDate start, LocalDate end) {
        JsonNode rooms = call(HttpMethod.GET,
                hotelUrl + "/api/rooms/recommend?startDate=%s&endDate=%s".formatted(start, end), token).getBody();
        return StreamSupport.stream(rooms.spliterator(), false).toList();
    }

    private static long timesBooked(String token, long roomId) {
        return call(HttpMethod.GET, hotelUrl + "/api/rooms/" + roomId, token).getBody().get("timesBooked").asLong();
    }

    private static JsonNode historyEntry(String token, String requestId) {
        JsonNode page = call(HttpMethod.GET, bookingUrl + "/api/bookings?size=100", token).getBody();
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(booking -> booking.get("requestId").asText().equals(requestId))
                .findFirst()
                .orElseThrow();
    }

    private static ResponseEntity<JsonNode> call(HttpMethod method, String url, String token) {
        return call(method, url, token, null);
    }

    private static ResponseEntity<JsonNode> call(HttpMethod method, String url, String token, Object body) {
        RestClient.RequestBodySpec request = HTTP.method(method).uri(url);
        if (token != null) {
            request.headers(headers -> headers.setBearerAuth(token));
        }
        if (body != null) {
            request.body(body);
        }
        return request.retrieve()
                .onStatus(status -> true, (req, res) -> {
                })
                .toEntity(JsonNode.class);
    }

    private static ConfigurableApplicationContext start(Class<?> application, String name, String... extraArgs) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.config.name=" + name,
                "--server.port=0",
                "--eureka.client.enabled=false",
                "--demo-data.enabled=true"));
        args.addAll(List.of(extraArgs));
        return new SpringApplicationBuilder(application).run(args.toArray(String[]::new));
    }

    private static String baseUrl(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return "http://localhost:" + port;
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
