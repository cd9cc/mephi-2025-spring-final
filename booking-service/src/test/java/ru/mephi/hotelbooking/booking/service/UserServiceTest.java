package ru.mephi.hotelbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.mephi.hotelbooking.booking.dto.auth.AuthRequest;
import ru.mephi.hotelbooking.booking.dto.auth.RegisterRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.dto.user.UserUpdateRequest;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.exception.InvalidCredentialsException;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.security.Role;

@SpringBootTest(properties = "eureka.client.enabled=false")
class UserServiceTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registeredUserGetsRoleUserAndHashedPassword() {
        UserResponse user = userService.register(new RegisterRequest("alice", "secret1"));

        assertThat(user.role()).isEqualTo(Role.USER);
        assertThat(userRepository.findById(user.id()).orElseThrow().getPassword()).isNotEqualTo("secret1");
    }

    @Test
    void duplicateUsernameIsRejected() {
        userService.register(new RegisterRequest("alice", "secret1"));

        assertThatThrownBy(() -> userService.register(new RegisterRequest("alice", "secret2")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void authenticationChecksPassword() {
        userService.register(new RegisterRequest("alice", "secret1"));

        assertThat(userService.authenticate(new AuthRequest("alice", "secret1")).username()).isEqualTo("alice");
        assertThatThrownBy(() -> userService.authenticate(new AuthRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> userService.authenticate(new AuthRequest("nobody", "secret1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void updateChangesOnlyGivenFields() {
        UserResponse user = userService.register(new RegisterRequest("alice", "secret1"));

        UserResponse updated = userService.update(new UserUpdateRequest(user.id(), null, null, Role.ADMIN));

        assertThat(updated.username()).isEqualTo("alice");
        assertThat(updated.role()).isEqualTo(Role.ADMIN);
        assertThat(userService.authenticate(new AuthRequest("alice", "secret1")).id()).isEqualTo(user.id());
    }

    @Test
    void userWithActiveBookingCannotBeDeleted() {
        UserResponse user = userService.register(new RegisterRequest("alice", "secret1"));
        saveBooking(user.id(), BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> userService.delete(user.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void userWithOnlyCancelledBookingsIsDeletedWithHistory() {
        UserResponse user = userService.register(new RegisterRequest("alice", "secret1"));
        saveBooking(user.id(), BookingStatus.CANCELLED);

        userService.delete(user.id());

        assertThat(userRepository.existsById(user.id())).isFalse();
        assertThat(bookingRepository.count()).isZero();
    }

    private void saveBooking(Long userId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setUser(userRepository.getReferenceById(userId));
        booking.setRoomId(1L);
        booking.setStartDate(LocalDate.now().plusDays(1));
        booking.setEndDate(LocalDate.now().plusDays(2));
        booking.setStatus(status);
        booking.setRequestId("req-" + status);
        bookingRepository.save(booking);
    }
}
