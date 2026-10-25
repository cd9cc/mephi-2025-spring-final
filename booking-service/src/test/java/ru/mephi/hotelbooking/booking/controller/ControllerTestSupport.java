package ru.mephi.hotelbooking.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.hotelbooking.booking.client.HotelClient;
import ru.mephi.hotelbooking.booking.dto.user.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.booking.service.TokenService;
import ru.mephi.hotelbooking.booking.service.UserService;
import ru.mephi.hotelbooking.common.security.Role;

@SpringBootTest
@AutoConfigureMockMvc
abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserService userService;
    @Autowired
    protected TokenService tokenService;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected BookingRepository bookingRepository;

    @MockitoBean
    protected HotelClient hotelClient;

    @AfterEach
    void cleanUp() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected UserResponse createUser(String username, Role role) {
        return userService.create(new UserCreateRequest(username, "password", role));
    }

    protected String tokenOf(UserResponse user) {
        return "Bearer " + tokenService.issue(user).accessToken();
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
