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

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.mephi.hotelbooking.booking.client.HotelClient;
import ru.mephi.hotelbooking.booking.client.HotelUnavailableException;
import ru.mephi.hotelbooking.booking.client.RoomUnavailableException;
import ru.mephi.hotelbooking.booking.client.dto.RoomCandidate;
import ru.mephi.hotelbooking.booking.dto.booking.BookingCreateRequest;
import ru.mephi.hotelbooking.booking.dto.booking.BookingResponse;
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
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.common.exception.ServiceUnavailableException;
import ru.mephi.hotelbooking.common.security.Role;

@SpringBootTest(properties = "eureka.client.enabled=false")
class BookingServiceTest {

    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = START.plusDays(2);

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
        userId = createUser("alice");
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void successfulHoldConfirmsBooking() {
        givenHoldSucceeds(7L);

        BookingResponse response = bookingService.create(userId, manual("req-1", 7L));

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.roomId()).isEqualTo(7L);
        verify(hotelClient).hold(eq(7L), argThat(request ->
                request.bookingId().equals(response.id())
                        && request.requestId().equals("booking-%d-room-7".formatted(response.id()))));
    }

    @Test
    void rejectedHoldCancelsBookingWithoutCompensation() {
        when(hotelClient.hold(eq(7L), any())).thenThrow(new RoomUnavailableException("busy"));

        assertThatThrownBy(() -> bookingService.create(userId, manual("req-1", 7L)))
                .isInstanceOf(ConflictException.class);

        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(singleBooking().getCancelReason()).isEqualTo(CancelReason.ROOM_UNAVAILABLE);
        verify(hotelClient, never()).release(any(), any());
    }

    @Test
    void failedHoldCancelsBookingAndReleasesRoom() {
        when(hotelClient.hold(eq(7L), any())).thenThrow(new HotelUnavailableException("timeout", null));

        assertThatThrownBy(() -> bookingService.create(userId, manual("req-1", 7L)))
                .isInstanceOf(ServiceUnavailableException.class);

        Booking booking = singleBooking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelReason()).isEqualTo(CancelReason.HOTEL_UNAVAILABLE);
        verify(hotelClient).release(eq(7L), argThat(request -> request.bookingId().equals(booking.getId())));
    }

    @Test
    void failedCompensationStillCancelsBooking() {
        when(hotelClient.hold(eq(7L), any())).thenThrow(new HotelUnavailableException("timeout", null));
        org.mockito.Mockito.doThrow(new HotelUnavailableException("timeout", null))
                .when(hotelClient).release(any(), any());

        assertThatThrownBy(() -> bookingService.create(userId, manual("req-1", 7L)))
                .isInstanceOf(ServiceUnavailableException.class);

        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void repeatedRequestReturnsSameBookingWithoutSecondHold() {
        givenHoldSucceeds(7L);
        BookingResponse first = bookingService.create(userId, manual("req-1", 7L));

        BookingResponse repeated = bookingService.create(userId, manual("req-1", 7L));

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(bookingRepository.count()).isEqualTo(1);
        verify(hotelClient, times(1)).hold(any(), any());
    }

    @Test
    void repeatedRequestOfRejectedBookingIsRejectedAgain() {
        when(hotelClient.hold(eq(7L), any())).thenThrow(new RoomUnavailableException("busy"));
        assertThatThrownBy(() -> bookingService.create(userId, manual("req-1", 7L)))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> bookingService.create(userId, manual("req-1", 7L)))
                .isInstanceOf(ConflictException.class);
        assertThat(bookingRepository.count()).isEqualTo(1);
        verify(hotelClient, times(1)).hold(any(), any());
    }

    @Test
    void concurrentDuplicatesOfOneRequestCreateOneBooking() throws Exception {
        givenHoldSucceeds(7L);
        int attempts = 8;
        List<Callable<BookingResponse>> tasks = IntStream.range(0, attempts)
                .<Callable<BookingResponse>>mapToObj(i -> () -> bookingService.create(userId, manual("req-1", 7L)))
                .toList();

        List<BookingResponse> responses;
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            responses = executor.invokeAll(tasks).stream().map(BookingServiceTest::await).toList();
        }

        assertThat(responses).extracting(BookingResponse::id).containsOnly(singleBooking().getId());
        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(hotelClient, times(1)).hold(any(), any());
    }

    @Test
    void requestIdOfAnotherUserIsRejected() {
        givenHoldSucceeds(7L);
        bookingService.create(userId, manual("req-1", 7L));
        Long otherUserId = createUser("bob");

        assertThatThrownBy(() -> bookingService.create(otherUserId, manual("req-1", 7L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void autoSelectTriesNextRecommendedRoomWhenFirstIsTaken() {
        when(hotelClient.recommendRooms(START, END)).thenReturn(List.of(candidate(3L), candidate(5L)));
        when(hotelClient.hold(eq(3L), any())).thenThrow(new RoomUnavailableException("busy"));
        givenHoldSucceeds(5L);

        BookingResponse response = bookingService.create(userId, autoSelect("req-1"));

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.roomId()).isEqualTo(5L);
    }

    @Test
    void autoSelectWithoutFreeRoomsIsRejectedWithoutBooking() {
        when(hotelClient.recommendRooms(START, END)).thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.create(userId, autoSelect("req-1")))
                .isInstanceOf(ConflictException.class);
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void cancelOfConfirmedBookingReleasesRoom() {
        givenHoldSucceeds(7L);
        BookingResponse booking = bookingService.create(userId, manual("req-1", 7L));

        BookingResponse cancelled = bookingService.cancel(userId, booking.id());

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.USER_CANCELLED);
        verify(hotelClient).release(eq(7L), argThat(request -> request.bookingId().equals(booking.id())));
    }

    @Test
    void cancelKeepsBookingConfirmedWhenReleaseFails() {
        givenHoldSucceeds(7L);
        BookingResponse booking = bookingService.create(userId, manual("req-1", 7L));
        org.mockito.Mockito.doThrow(new HotelUnavailableException("down", null))
                .when(hotelClient).release(any(), any());

        assertThatThrownBy(() -> bookingService.cancel(userId, booking.id()))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(singleBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void bookingOfAnotherUserIsNotVisible() {
        givenHoldSucceeds(7L);
        BookingResponse booking = bookingService.create(userId, manual("req-1", 7L));
        Long otherUserId = createUser("bob");

        assertThatThrownBy(() -> bookingService.get(otherUserId, booking.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> bookingService.cancel(otherUserId, booking.id()))
                .isInstanceOf(NotFoundException.class);
    }

    private void givenHoldSucceeds(Long roomId) {
        when(hotelClient.hold(eq(roomId), any())).thenAnswer(invocation -> {
            AvailabilityRequest request = invocation.getArgument(1);
            return new ReservationResponse(100L, roomId, request.bookingId(),
                    request.startDate(), request.endDate(), ReservationStatus.ACTIVE);
        });
    }

    private Long createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("hash");
        user.setRole(Role.USER);
        return userRepository.save(user).getId();
    }

    private Booking singleBooking() {
        assertThat(bookingRepository.count()).isEqualTo(1);
        return bookingRepository.findAll().getFirst();
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static BookingCreateRequest manual(String requestId, Long roomId) {
        return new BookingCreateRequest(requestId, roomId, false, START, END);
    }

    private static BookingCreateRequest autoSelect(String requestId) {
        return new BookingCreateRequest(requestId, null, true, START, END);
    }

    private static RoomCandidate candidate(Long roomId) {
        return new RoomCandidate(roomId, 1L, "R" + roomId, 0);
    }
}
