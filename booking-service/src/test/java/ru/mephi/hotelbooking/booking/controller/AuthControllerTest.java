package ru.mephi.hotelbooking.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;
import ru.mephi.hotelbooking.booking.dto.auth.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.auth.RegisterRequest;
import ru.mephi.hotelbooking.common.security.JwtClaims;

class AuthControllerTest extends ControllerTestSupport {

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void registrationReturnsOneHourUserToken() throws Exception {
        String response = register(new RegisterRequest("alice", "secret1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(objectMapper.readTree(response).get("accessToken").asText());
        Long userId = userRepository.findByUsername("alice").orElseThrow().getId();
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString(JwtClaims.ROLE)).isEqualTo("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void issuedTokenGivesAccessToProtectedEndpoints() throws Exception {
        register(new RegisterRequest("alice", "secret1"));
        String response = authenticate(new AuthRequest("alice", "secret1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        register(new RegisterRequest("alice", "secret1"));

        register(new RegisterRequest("alice", "secret2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username alice is already taken"));
    }

    @Test
    void invalidRegistrationIsBadRequest() throws Exception {
        register(new RegisterRequest("al", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        register(new RegisterRequest("alice", "secret1"));

        authenticate(new AuthRequest("alice", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/bookings/user/auth").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private ResultActions register(RegisterRequest request) throws Exception {
        return mockMvc.perform(post("/api/bookings/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private ResultActions authenticate(AuthRequest request) throws Exception {
        return mockMvc.perform(post("/api/bookings/user/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }
}
