package ru.mephi.hotelbooking.booking.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import ru.mephi.hotelbooking.booking.client.dto.RoomCandidate;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;

@Component
public class HttpHotelClient implements HotelClient {

    static final String RESILIENCE_INSTANCE = "hotel";

    private static final ParameterizedTypeReference<List<RoomCandidate>> ROOM_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public HttpHotelClient(@Qualifier("hotelRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public List<RoomCandidate> recommendRooms(LocalDate startDate, LocalDate endDate) {
        return call(() -> restClient.get()
                .uri(uri -> uri.path("/api/rooms/recommend")
                        .queryParam("startDate", startDate)
                        .queryParam("endDate", endDate)
                        .build())
                .retrieve()
                .body(ROOM_LIST));
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public ReservationResponse hold(Long roomId, AvailabilityRequest request) {
        return call(() -> restClient.post()
                .uri("/api/rooms/{id}/confirm-availability", roomId)
                .body(request)
                .retrieve()
                .body(ReservationResponse.class));
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public void release(Long roomId, ReleaseRequest request) {
        call(() -> restClient.post()
                .uri("/api/rooms/{id}/release", roomId)
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException.Conflict | HttpClientErrorException.NotFound e) {
            throw new RoomUnavailableException(e.getResponseBodyAsString());
        } catch (HttpClientErrorException e) {
            throw new HotelClientException("Hotel service rejected the request: " + e.getStatusCode(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new HotelUnavailableException("Hotel service is unavailable: " + e.getMessage(), e);
        }
    }
}
