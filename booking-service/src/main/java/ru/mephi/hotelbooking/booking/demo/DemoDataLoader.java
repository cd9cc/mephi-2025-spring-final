package ru.mephi.hotelbooking.booking.demo;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.CancelReason;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.common.security.Role;

@Slf4j
@Component
@ConditionalOnProperty(name = "demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        user("admin", "admin123", Role.ADMIN);
        User user = user("user", "user123", Role.USER);
        user("alice", "alice123", Role.USER);

        LocalDate today = LocalDate.now();
        booking(user, 1L, today.plusDays(1), today.plusDays(4), null);
        booking(user, 4L, today.minusDays(10), today.minusDays(7), null);
        booking(user, 2L, today.plusDays(5), today.plusDays(7), CancelReason.USER_CANCELLED);

        log.info("Demo data loaded: {} users, {} bookings", userRepository.count(), bookingRepository.count());
    }

    private User user(String username, String password, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.save(user);
    }

    private void booking(User user, Long roomId, LocalDate start, LocalDate end, CancelReason cancelReason) {
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoomId(roomId);
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setRequestId("demo-%d-%s".formatted(roomId, start));
        booking.setStatus(cancelReason == null ? BookingStatus.CONFIRMED : BookingStatus.CANCELLED);
        booking.setCancelReason(cancelReason);
        bookingRepository.save(booking);
    }
}
