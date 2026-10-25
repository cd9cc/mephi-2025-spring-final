package ru.mephi.hotelbooking.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.mephi.hotelbooking.booking.client.HotelUnavailableException;
import ru.mephi.hotelbooking.booking.client.RoomUnavailableException;
import ru.mephi.hotelbooking.booking.client.dto.RoomCandidate;
import ru.mephi.hotelbooking.booking.dto.booking.BookingCreateRequest;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.common.security.Role;
import ru.mephi.hotelbooking.common.trace.TraceContext;

class BookingControllerTest extends ControllerTestSupport {

    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = START.plusDays(2);

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        aliceToken = tokenOf(createUser("alice", Role.USER));
        bobToken = tokenOf(createUser("bob", Role.USER));
        when(hotelClient.hold(any(), any())).thenAnswer(invocation -> {
            Long roomId = invocation.getArgument(0);
            AvailabilityRequest request = invocation.getArgument(1);
            return new ReservationResponse(100L, roomId, request.bookingId(),
                    request.startDate(), request.endDate(), ReservationStatus.ACTIVE);
        });
    }

    @Test
    void userCreatesConfirmedBooking() throws Exception {
        create(aliceToken, manual("req-1", 7L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(7))
                .andExpect(jsonPath("$.requestId").value("req-1"));
    }

    @Test
    void autoSelectBooksFirstRecommendedRoom() throws Exception {
        when(hotelClient.recommendRooms(START, END))
                .thenReturn(List.of(new RoomCandidate(3L, 1L, "103", 0), new RoomCandidate(5L, 1L, "105", 2)));

        create(aliceToken, new BookingCreateRequest("req-1", 99L, true, START, END))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(3));
    }

    @Test
    void adminCanBookAsUser() throws Exception {
        create(tokenOf(createUser("admin", Role.ADMIN)), manual("req-1", 7L))
                .andExpect(status().isCreated());
    }

    @Test
    void anonymousCannotBook() throws Exception {
        mockMvc.perform(post("/api/bookings/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(manual("req-1", 7L))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedRequestReturnsSameBookingWithoutSecondHold() throws Exception {
        String first = create(aliceToken, manual("req-1", 7L)).andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(first).get("id").asLong();

        create(aliceToken, manual("req-1", 7L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId));

        assertThat(bookingRepository.count()).isEqualTo(1);
        verify(hotelClient, times(1)).hold(any(), any());
    }

    @Test
    void invalidDatesAreRejectedWithFieldErrors() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        create(aliceToken, new BookingCreateRequest("req-1", 7L, false, yesterday, yesterday))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", containsInAnyOrder("startDate", "bookingCreateRequest")));
    }

    @Test
    void roomIsRequiredWithoutAutoSelect() throws Exception {
        create(aliceToken, new BookingCreateRequest("req-1", null, false, START, END))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].message").value("roomId is required when autoSelect is false"));
    }

    @Test
    void occupiedRoomIsConflict() throws Exception {
        doThrow(new RoomUnavailableException("busy")).when(hotelClient).hold(eq(7L), any());

        create(aliceToken, manual("req-1", 7L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(bookingRepository.findAll()).extracting("status").containsExactly(BookingStatus.CANCELLED);
    }

    @Test
    void unavailableHotelIsServiceUnavailableAndBookingIsCompensated() throws Exception {
        doThrow(new HotelUnavailableException("timeout", null)).when(hotelClient).hold(eq(7L), any());

        create(aliceToken, manual("req-1", 7L))
                .andExpect(status().isServiceUnavailable());

        assertThat(bookingRepository.findAll()).extracting("status").containsExactly(BookingStatus.CANCELLED);
        verify(hotelClient).release(eq(7L), any());
    }

    @Test
    void historyContainsOnlyOwnBookingsPageByPage() throws Exception {
        create(aliceToken, manual("a-1", 1L));
        create(aliceToken, manual("a-2", 2L));
        create(aliceToken, manual("a-3", 3L));
        create(bobToken, manual("b-1", 4L));

        mockMvc.perform(get("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, aliceToken)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].requestId").value("a-3"));
    }

    @Test
    void bookingOfAnotherUserIsNotFound() throws Exception {
        long bookingId = bookingIdOf(create(bobToken, manual("b-1", 4L)));

        mockMvc.perform(get("/api/bookings/booking/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/bookings/booking/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void userGetsAndCancelsOwnBooking() throws Exception {
        long bookingId = bookingIdOf(create(aliceToken, manual("req-1", 7L)));

        mockMvc.perform(get("/api/bookings/booking/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(delete("/api/bookings/booking/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("USER_CANCELLED"));
        verify(hotelClient).release(eq(7L), any());
    }

    @Test
    void traceIdOfRequestIsReturnedInHeaderAndError() throws Exception {
        mockMvc.perform(get("/api/bookings/booking/{id}", 999)
                        .header(HttpHeaders.AUTHORIZATION, aliceToken)
                        .header(TraceContext.TRACE_ID_HEADER, "client-trace-1"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, "client-trace-1"))
                .andExpect(jsonPath("$.traceId").value("client-trace-1"));
    }

    private ResultActions create(String token, BookingCreateRequest request) throws Exception {
        return mockMvc.perform(post("/api/bookings/booking")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private long bookingIdOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static BookingCreateRequest manual(String requestId, Long roomId) {
        return new BookingCreateRequest(requestId, roomId, false, START, END);
    }
}
