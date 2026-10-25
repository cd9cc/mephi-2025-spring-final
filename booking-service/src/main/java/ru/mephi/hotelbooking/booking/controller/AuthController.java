package ru.mephi.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.booking.dto.auth.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.auth.RegisterRequest;
import ru.mephi.hotelbooking.booking.dto.auth.TokenResponse;
import ru.mephi.hotelbooking.booking.service.TokenService;
import ru.mephi.hotelbooking.booking.service.UserService;

@Tag(name = "Authentication")
@SecurityRequirements
@RestController
@RequestMapping("/api/bookings/user")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;

    @Operation(summary = "Register a user with role USER and get a token")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return tokenService.issue(userService.register(request));
    }

    @Operation(summary = "Log in and get a token valid for one hour")
    @PostMapping("/auth")
    public TokenResponse authenticate(@Valid @RequestBody AuthRequest request) {
        return tokenService.issue(userService.authenticate(request));
    }
}
