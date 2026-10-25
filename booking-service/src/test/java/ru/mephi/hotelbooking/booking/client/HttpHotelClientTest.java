package ru.mephi.hotelbooking.booking.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.common.security.JwtClaims;
import ru.mephi.hotelbooking.common.trace.TraceContext;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "hotel-client.read-timeout=300ms",
        "resilience4j.retry.instances.hotel.wait-duration=10ms"
})
class HttpHotelClientTest {

    private static final HttpServer SERVER = startServer();
    private static final Queue<StubResponse> RESPONSES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<Headers> LAST_HEADERS = new AtomicReference<>();

    private static final AvailabilityRequest REQUEST =
            new AvailabilityRequest("req-1", 1L, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3));
    private static final String RESERVATION_JSON = """
            {"reservationId":10,"roomId":7,"bookingId":1,
             "startDate":"2030-01-01","endDate":"2030-01-03","status":"ACTIVE"}
            """;

    @Autowired
    private HotelClient hotelClient;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void hotelInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri",
                () -> "http://localhost:" + SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void setUp() {
        RESPONSES.clear();
        CALLS.set(0);
        circuitBreakerRegistry.circuitBreaker(HttpHotelClient.RESILIENCE_INSTANCE).reset();
    }

    @Test
    void callsCarryServiceTokenAndTraceId() {
        respond(200, RESERVATION_JSON);

        try (var ignored = MDC.putCloseable(TraceContext.TRACE_ID_MDC_KEY, "trace-123")) {
            hotelClient.hold(7L, REQUEST);
        }

        Headers headers = LAST_HEADERS.get();
        assertThat(headers.getFirst(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-123");
        Jwt token = jwtDecoder.decode(headers.getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length()));
        assertThat(token.getClaimAsString(JwtClaims.SCOPE)).isEqualTo(JwtClaims.INTERNAL_SCOPE);
        assertThat(token.getSubject()).isEqualTo("booking-service");
    }

    @Test
    void transientErrorsAreRetried() {
        respond(503, "");
        respond(500, "");
        respond(200, RESERVATION_JSON);

        ReservationResponse response = hotelClient.hold(7L, REQUEST);

        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(CALLS).hasValue(3);
    }

    @Test
    void retriesAreLimited() {
        respondAlways(500);

        assertThatThrownBy(() -> hotelClient.hold(7L, REQUEST)).isInstanceOf(HotelUnavailableException.class);
        assertThat(CALLS).hasValue(3);
    }

    @Test
    void serviceUnavailableIsRetriedOnlyByRetryPolicy() {
        respondAlways(503);

        assertThatThrownBy(() -> hotelClient.hold(7L, REQUEST)).isInstanceOf(HotelUnavailableException.class);
        assertThat(CALLS).hasValue(3);
    }

    @Test
    void timeoutIsRetriedAndReportedAsUnavailable() {
        respondAlways(-1);

        assertThatThrownBy(() -> hotelClient.hold(7L, REQUEST)).isInstanceOf(HotelUnavailableException.class);
        assertThat(CALLS).hasValue(3);
    }

    @Test
    void conflictIsNotRetried() {
        respond(409, "{\"message\":\"busy\"}");

        assertThatThrownBy(() -> hotelClient.hold(7L, REQUEST)).isInstanceOf(RoomUnavailableException.class);
        assertThat(CALLS).hasValue(1);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingHotel() {
        respondAlways(500);
        assertThatThrownBy(() -> hotelClient.hold(7L, REQUEST)).isInstanceOf(HotelUnavailableException.class);

        assertThatThrownBy(() -> hotelClient.hold(7L, REQUEST)).isInstanceOf(CallNotPermittedException.class);
        assertThat(CALLS).hasValue(5);

        assertThatThrownBy(() -> hotelClient.hold(7L, REQUEST)).isInstanceOf(CallNotPermittedException.class);
        assertThat(CALLS).hasValue(5);
    }

    private static void respond(int status, String body) {
        RESPONSES.add(new StubResponse(status, body));
    }

    private static void respondAlways(int status) {
        for (int i = 0; i < 20; i++) {
            respond(status, "");
        }
    }

    private record StubResponse(int status, String body) {
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                CALLS.incrementAndGet();
                LAST_HEADERS.set(exchange.getRequestHeaders());
                StubResponse response = RESPONSES.poll();
                if (response == null || response.status() == -1) {
                    sleepQuietly();
                    response = new StubResponse(500, "");
                }
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
