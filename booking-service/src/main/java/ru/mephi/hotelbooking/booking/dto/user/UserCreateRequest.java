package ru.mephi.hotelbooking.booking.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mephi.hotelbooking.common.security.Role;

public record UserCreateRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotNull Role role
) {
}
