package ru.mephi.hotelbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.mephi.hotelbooking.booking.client.HotelClient;
import ru.mephi.hotelbooking.booking.dto.booking.BookingCreateRequest;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.CancelReason;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.ServiceUnavailableException;
import ru.mephi.hotelbooking.common.security.Role;

@SpringBootTest(properties = "eureka.client.enabled=false")
class PendingBookingRecoveryTest {

    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = START.plusDays(2);

    @Autowired
    private PendingBookingRecovery recovery;
    @Autowired
    private BookingStateManager stateManager;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @MockitoBean
    private HotelClient hotelClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("hash");
        user.setRole(Role.USER);
        userId = userRepository.save(user).getId();
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void stuckPendingBookingIsCancelledAndRoomReleased() {
        Booking stuck = stateManager.createPending(userId, 7L, request("req-1"));

        recovery.recover(Instant.now().plusSeconds(1));

        Booking booking = reload(stuck);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelReason()).isEqualTo(CancelReason.PROCESSING_TIMEOUT);
        verify(hotelClient).release(eq(7L), argThat(release -> release.bookingId().equals(stuck.getId())));
    }

    @Test
    void recentPendingBookingIsLeftAlone() {
        Booking pending = stateManager.createPending(userId, 7L, request("req-1"));

        recovery.recover(Instant.now().minusSeconds(60));

        assertThat(reload(pending).getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(hotelClient, never()).release(any(), any());
    }

    @Test
    void sagaFinishingAfterRecoveryCannotConfirmAndReleasesItsHold() {
        when(hotelClient.hold(eq(7L), any())).thenAnswer(invocation -> {
            recovery.recover(Instant.now().plusSeconds(1));
            AvailabilityRequest hold = invocation.getArgument(1);
            return new ReservationResponse(100L, 7L, hold.bookingId(),
                    hold.startDate(), hold.endDate(), ReservationStatus.ACTIVE);
        });

        assertThatThrownBy(() -> bookingService.create(userId, request("req-1")))
                .isInstanceOf(ConflictException.class);

        Booking booking = bookingRepository.findByRequestId("req-1").orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelReason()).isEqualTo(CancelReason.PROCESSING_TIMEOUT);
        verify(hotelClient, times(2)).release(eq(7L), any());
    }

    @Test
    void repeatedRequestOfRecoveredBookingIsRejected() {
        stateManager.createPending(userId, 7L, request("req-1"));
        recovery.recover(Instant.now().plusSeconds(1));

        assertThatThrownBy(() -> bookingService.create(userId, request("req-1")))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(hotelClient, never()).hold(any(), any());
    }

    private Booking reload(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    private static BookingCreateRequest request(String requestId) {
        return new BookingCreateRequest(requestId, 7L, false, START, END);
    }
}
