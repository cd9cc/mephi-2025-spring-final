package ru.mephi.hotelbooking.booking.service;

import ru.mephi.hotelbooking.booking.dto.auth.TokenResponse;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;

public interface TokenService {

    TokenResponse issue(UserResponse user);

    String issueServiceToken();
}
