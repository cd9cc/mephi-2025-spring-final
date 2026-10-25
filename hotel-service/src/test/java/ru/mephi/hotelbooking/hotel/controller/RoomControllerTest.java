package ru.mephi.hotelbooking.hotel.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;

class RoomControllerTest extends ControllerTestSupport {

    private static final LocalDate START = LocalDate.of(2030, 5, 10);
    private static final LocalDate END = START.plusDays(4);

    private Hotel hotel;

    @BeforeEach
    void setUp() {
        hotel = hotel("Grand", "Moscow");
    }

    @Test
    void adminCreatesRoom() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoomRequest(hotel.getId(), "101", 2, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));
    }

    @Test
    void duplicateRoomNumberIsConflict() throws Exception {
        room(hotel, "101", 0);

        mockMvc.perform(post("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoomRequest(hotel.getId(), "101", 2, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void userCannotUpdateRoom() throws Exception {
        Room room = room(hotel, "101", 0);

        mockMvc.perform(put("/api/rooms/{id}", room.getId())
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RoomRequest(hotel.getId(), "101", 2, false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void freeRoomsExcludeReservedAndOutOfServiceRooms() throws Exception {
        Room reserved = room(hotel, "101", 0);
        Room free = room(hotel, "102", 0);
        Room outOfService = room(hotel, "103", 0);
        outOfService.setAvailable(false);
        roomRepository.save(outOfService);
        reserve(reserved, START.plusDays(1), END.plusDays(1));

        mockMvc.perform(get("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("startDate", START.toString())
                        .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(free.getId().intValue())));
    }

    @Test
    void recommendedRoomsAreSortedByTimesBookedThenById() throws Exception {
        Room loaded = room(hotel, "101", 5);
        Room firstIdle = room(hotel, "102", 1);
        Room secondIdle = room(hotel, "103", 1);

        mockMvc.perform(get("/api/rooms/recommend")
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("startDate", START.toString())
                        .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        firstIdle.getId().intValue(), secondIdle.getId().intValue(), loaded.getId().intValue())));
    }

    @Test
    void bookingServiceTokenMayRequestRecommendations() throws Exception {
        mockMvc.perform(get("/api/rooms/recommend").header(HttpHeaders.AUTHORIZATION, serviceToken()))
                .andExpect(status().isOk());
    }

    @Test
    void endDateBeforeStartDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("startDate", END.toString())
                        .param("endDate", START.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].message").value("endDate must be after startDate"));
    }

    @Test
    void periodWithoutEndDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("startDate", START.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("periodComplete"));
    }
}
