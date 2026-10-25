package ru.mephi.hotelbooking.booking.service.impl;

import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.booking.dto.auth.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.auth.RegisterRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.dto.user.UserUpdateRequest;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.exception.InvalidCredentialsException;
import ru.mephi.hotelbooking.booking.mapper.UserMapper;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.booking.service.UserService;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.common.security.Role;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final EnumSet<BookingStatus> ACTIVE_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        return createUser(request.username(), request.password(), Role.USER);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse authenticate(AuthRequest request) {
        return userRepository.findByUsername(request.username())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .map(userMapper::toResponse)
                .orElseThrow(InvalidCredentialsException::new);
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        return createUser(request.username(), request.password(), request.role());
    }

    @Override
    @Transactional
    public UserResponse update(UserUpdateRequest request) {
        User user = findUser(request.id());
        if (request.username() != null) {
            if (userRepository.existsByUsernameAndIdNot(request.username(), user.getId())) {
                throw duplicateUsername(request.username());
            }
            user.setUsername(request.username());
        }
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        log.info("User {} updated", user.getId());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = findUser(id);
        if (bookingRepository.existsByUserIdAndStatusIn(id, ACTIVE_STATUSES)) {
            throw new ConflictException("User %d has active bookings and cannot be deleted".formatted(id));
        }
        bookingRepository.deleteByUserId(id);
        userRepository.delete(user);
        log.info("User {} deleted", id);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userMapper.toResponse(findUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    private UserResponse createUser(String username, String rawPassword, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw duplicateUsername(username);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        userRepository.save(user);
        log.info("User {} created with role {}", user.getId(), role);
        return userMapper.toResponse(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private static ConflictException duplicateUsername(String username) {
        return new ConflictException("Username %s is already taken".formatted(username));
    }
}
