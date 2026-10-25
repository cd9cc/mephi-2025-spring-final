package ru.mephi.hotelbooking.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.mephi.hotelbooking.booking.dto.user.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.dto.user.UserUpdateRequest;
import ru.mephi.hotelbooking.common.security.Role;

class UserControllerTest extends ControllerTestSupport {

    private String adminToken;
    private UserResponse user;

    @BeforeEach
    void setUp() {
        adminToken = tokenOf(createUser("admin", Role.ADMIN));
        user = createUser("alice", Role.USER);
    }

    @Test
    void adminCreatesUserWithRole() throws Exception {
        mockMvc.perform(post("/api/bookings/user")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserCreateRequest("bob", "secret1", Role.ADMIN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void adminUpdatesUserPartially() throws Exception {
        mockMvc.perform(patch("/api/bookings/user")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserUpdateRequest(user.id(), "alice2", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice2"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void adminDeletesUser() throws Exception {
        mockMvc.perform(delete("/api/bookings/user")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .param("id", user.id().toString()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.existsById(user.id())).isFalse();
    }

    @Test
    void deletingMissingUserIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/bookings/user")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .param("id", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminListsUsersPageByPage() throws Exception {
        mockMvc.perform(get("/api/bookings/user")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void userCannotManageUsers() throws Exception {
        String userToken = tokenOf(user);

        mockMvc.perform(post("/api/bookings/user")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserCreateRequest("bob", "secret1", Role.ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/api/bookings/user").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotManageUsers() throws Exception {
        mockMvc.perform(delete("/api/bookings/user").param("id", user.id().toString()))
                .andExpect(status().isUnauthorized());
    }
}
