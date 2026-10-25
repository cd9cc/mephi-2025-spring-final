package ru.mephi.hotelbooking.hotel.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;

class RoomReservationControllerTest extends ControllerTestSupport {

    private static final LocalDate START = LocalDate.of(2030, 5, 10);
    private static final LocalDate END = START.plusDays(3);

    private Long roomId;

    @BeforeEach
    void setUp() {
        roomId = room(hotel("Grand", "Moscow"), "101", 0).getId();
    }

    @Test
    void serviceTokenHoldsRoom() throws Exception {
        hold(serviceToken(), new AvailabilityRequest("req-1", 1L, START, END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bookingId").value(1));
    }

    @Test
    void userTokenCannotCallInternalEndpoints() throws Exception {
        hold(userToken(), new AvailabilityRequest("req-1", 1L, START, END))
                .andExpect(status().isForbidden());
        hold(adminToken(), new AvailabilityRequest("req-1", 1L, START, END))
                .andExpect(status().isForbidden());
    }

    @Test
    void repeatedHoldReturnsSameReservation() throws Exception {
        var request = new AvailabilityRequest("req-1", 1L, START, END);
        String first = hold(serviceToken(), request).andReturn().getResponse().getContentAsString();
        long reservationId = objectMapper.readTree(first).get("reservationId").asLong();

        hold(serviceToken(), request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId));
    }

    @Test
    void overlappingHoldIsConflict() throws Exception {
        hold(serviceToken(), new AvailabilityRequest("req-1", 1L, START, END));

        hold(serviceToken(), new AvailabilityRequest("req-2", 2L, START.plusDays(1), END.plusDays(1)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidHoldRequestIsBadRequest() throws Exception {
        hold(serviceToken(), new AvailabilityRequest("", 1L, END, START))
                .andExpect(status().isBadRequest());
    }

    @Test
    void releaseFreesHoldAndWithoutHoldReturnsNoContent() throws Exception {
        hold(serviceToken(), new AvailabilityRequest("req-1", 1L, START, END));

        release(new ReleaseRequest("rel-1", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        release(new ReleaseRequest("rel-2", 99L))
                .andExpect(status().isNoContent());
    }

    private ResultActions hold(String token, AvailabilityRequest request) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/confirm-availability", roomId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private ResultActions release(ReleaseRequest request) throws Exception {
        return mockMvc.perform(post("/api/rooms/{id}/release", roomId)
                .header(HttpHeaders.AUTHORIZATION, serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }
}
