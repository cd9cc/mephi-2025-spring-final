package ru.mephi.hotelbooking.booking.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mephi.hotelbooking.common.security.Role;

public record UserUpdateRequest(
        @NotNull Long id,
        @Size(min = 3, max = 50) String username,
        @Size(min = 6, max = 100) String password,
        Role role
) {
}
