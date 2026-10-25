package ru.mephi.hotelbooking.booking.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.mephi.hotelbooking.booking.dto.auth.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.auth.RegisterRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.dto.user.UserUpdateRequest;

public interface UserService {

    UserResponse register(RegisterRequest request);

    UserResponse authenticate(AuthRequest request);

    UserResponse create(UserCreateRequest request);

    UserResponse update(UserUpdateRequest request);

    void delete(Long id);

    UserResponse get(Long id);

    Page<UserResponse> list(Pageable pageable);
}
