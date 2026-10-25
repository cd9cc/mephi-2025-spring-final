package ru.mephi.hotelbooking.hotel.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;

class StatisticsControllerTest extends ControllerTestSupport {

    private static final LocalDate START = LocalDate.of(2030, 5, 10);
    private static final LocalDate END = START.plusDays(4);

    private Hotel grand;
    private Hotel quiet;
    private Room busy;
    private Room idle;

    @BeforeEach
    void setUp() {
        grand = hotel("Grand", "Moscow");
        busy = room(grand, "101", 3);
        idle = room(grand, "102", 1);
        Room outOfService = room(grand, "103", 0);
        outOfService.setAvailable(false);
        roomRepository.save(outOfService);
        reserve(busy, START.minusDays(1), START.plusDays(2));

        quiet = hotel("Quiet", "Kazan");
        room(quiet, "1", 0);
    }

    @Test
    void roomStatisticsListMostLoadedRoomsFirst() throws Exception {
        statistics("/api/rooms/statistics", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(busy.getId()))
                .andExpect(jsonPath("$[0].bookedNights").value(2))
                .andExpect(jsonPath("$[0].occupancyRate").value(0.5));
    }

    @Test
    void hotelStatisticsCountOnlyRoomsInService() throws Exception {
        statistics("/api/hotels/statistics", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].hotelId", contains(grand.getId().intValue(), quiet.getId().intValue())))
                .andExpect(jsonPath("$[0].rooms").value(3))
                .andExpect(jsonPath("$[0].roomsInService").value(2))
                .andExpect(jsonPath("$[0].idleRooms").value(1))
                .andExpect(jsonPath("$[0].bookedNights").value(2))
                .andExpect(jsonPath("$[0].occupancyRate").value(0.25))
                .andExpect(jsonPath("$[0].averageTimesBooked").value(2.0))
                .andExpect(jsonPath("$[1].occupancyRate").value(0.0));
    }

    @Test
    void statisticsOfOneHotel() throws Exception {
        mockMvc.perform(get("/api/hotels/statistics")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .param("hotelId", quiet.getId().toString())
                        .param("startDate", START.toString())
                        .param("endDate", END.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idleRooms").value(1));
    }

    @Test
    void statisticsOfMissingHotelIsNotFound() throws Exception {
        mockMvc.perform(get("/api/hotels/statistics")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .param("hotelId", "999")
                        .param("startDate", START.toString())
                        .param("endDate", END.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void periodIsRequired() throws Exception {
        mockMvc.perform(get("/api/hotels/statistics").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userCannotSeeStatistics() throws Exception {
        statistics("/api/rooms/statistics", userToken()).andExpect(status().isForbidden());
        statistics("/api/hotels/statistics", userToken()).andExpect(status().isForbidden());
    }

    private ResultActions statistics(String path, String token) throws Exception {
        return mockMvc.perform(get(path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .param("startDate", START.toString())
                .param("endDate", END.toString()));
    }
}
