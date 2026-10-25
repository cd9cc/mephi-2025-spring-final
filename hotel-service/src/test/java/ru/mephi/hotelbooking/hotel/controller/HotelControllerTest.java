package ru.mephi.hotelbooking.hotel.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.mephi.hotelbooking.common.trace.TraceContext;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.entity.Hotel;

class HotelControllerTest extends ControllerTestSupport {

    private static final HotelRequest HOTEL = new HotelRequest("Grand", "Main st. 1", "Moscow");

    @Test
    void adminCreatesHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(HOTEL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.name").value("Grand"));
    }

    @Test
    void userCannotCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(HOTEL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/hotels"));
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String token = adminTokenSignedWith("another-secret-another-secret-another-secret");

        mockMvc.perform(get("/api/hotels").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/hotels").header(HttpHeaders.AUTHORIZATION, expiredAdminToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    void invalidHotelIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new HotelRequest(" ", "Main st. 1", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void userListsHotelsFilteredByCityPageByPage() throws Exception {
        hotel("A", "Moscow");
        hotel("B", "Moscow");
        hotel("C", "Kazan");

        mockMvc.perform(get("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("city", "moscow")
                        .param("size", "1")
                        .param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("B"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void sortingByUnknownPropertyIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/hotels")
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .param("sort", "password"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingHotelIsNotFound() throws Exception {
        mockMvc.perform(get("/api/hotels/{id}", 999)
                        .header(HttpHeaders.AUTHORIZATION, userToken())
                        .header(TraceContext.TRACE_ID_HEADER, "trace-from-client"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, "trace-from-client"))
                .andExpect(jsonPath("$.traceId").value("trace-from-client"))
                .andExpect(jsonPath("$.message").value("Hotel with id 999 not found"));
    }

    @Test
    void adminDeletesHotel() throws Exception {
        Hotel hotel = hotel("A", "Moscow");

        mockMvc.perform(delete("/api/hotels/{id}", hotel.getId()).header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNoContent());
    }
}
