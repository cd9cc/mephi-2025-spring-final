package ru.mephi.hotelbooking.booking.dto.user;

import java.time.Instant;
import ru.mephi.hotelbooking.common.security.Role;

public record UserResponse(
        Long id,
        String username,
        Role role,
        Instant createdAt
) {
}
