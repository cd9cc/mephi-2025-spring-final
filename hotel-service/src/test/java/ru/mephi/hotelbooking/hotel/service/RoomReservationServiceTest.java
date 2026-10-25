package ru.mephi.hotelbooking.hotel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomReservationRepository;

@SpringBootTest(properties = "eureka.client.enabled=false")
class RoomReservationServiceTest {

    private static final LocalDate START = LocalDate.of(2030, 5, 10);
    private static final LocalDate END = START.plusDays(3);

    @Autowired
    private RoomReservationService reservationService;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomReservationRepository reservationRepository;

    private Long roomId;

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setName("Test");
        hotel.setAddress("Street 1");
        hotelRepository.save(hotel);

        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber("101");
        room.setCapacity(2);
        roomId = roomRepository.save(room).getId();
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    @Test
    void holdCreatesActiveReservationAndIncrementsTimesBooked() {
        ReservationResponse response = reservationService.hold(roomId, request("req-1", 1L, START, END));

        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(response.bookingId()).isEqualTo(1L);
        assertThat(timesBooked()).isEqualTo(1);
    }

    @Test
    void repeatedHoldWithSameRequestIdReturnsSameReservation() {
        ReservationResponse first = reservationService.hold(roomId, request("req-1", 1L, START, END));

        ReservationResponse repeated = reservationService.hold(roomId, request("req-1", 1L, START, END));

        assertThat(repeated.reservationId()).isEqualTo(first.reservationId());
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(timesBooked()).isEqualTo(1);
    }

    @Test
    void holdOfOverlappingPeriodIsRejected() {
        reservationService.hold(roomId, request("req-1", 1L, START, END));

        assertThatThrownBy(() -> reservationService.hold(roomId, request("req-2", 2L, START.plusDays(1), END)))
                .isInstanceOf(ConflictException.class);
        assertThat(timesBooked()).isEqualTo(1);
    }

    @Test
    void holdOfRoomOutOfServiceIsRejected() {
        Room room = roomRepository.findById(roomId).orElseThrow();
        room.setAvailable(false);
        roomRepository.save(room);

        assertThatThrownBy(() -> reservationService.hold(roomId, request("req-1", 1L, START, END)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void releaseFreesSlotAndIsIdempotent() {
        Room room = roomRepository.findById(roomId).orElseThrow();
        room.setTimesBooked(5);
        roomRepository.save(room);
        reservationService.hold(roomId, request("req-1", 1L, START, END));

        var released = reservationService.release(roomId, new ReleaseRequest("rel-1", 1L));
        var releasedAgain = reservationService.release(roomId, new ReleaseRequest("rel-1", 1L));

        assertThat(released).get().extracting(ReservationResponse::status).isEqualTo(ReservationStatus.RELEASED);
        assertThat(releasedAgain).get().extracting(ReservationResponse::status).isEqualTo(ReservationStatus.RELEASED);
        assertThat(timesBooked()).isEqualTo(5);
        assertThat(reservationService.hold(roomId, request("req-2", 2L, START, END)).status())
                .isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void releaseWithoutHoldIsNoOp() {
        assertThat(reservationService.release(roomId, new ReleaseRequest("rel-1", 1L))).isEmpty();
    }

    @Test
    void lateDuplicateHoldAfterReleaseDoesNotReserveAgain() {
        reservationService.hold(roomId, request("req-1", 1L, START, END));
        reservationService.release(roomId, new ReleaseRequest("rel-1", 1L));

        ReservationResponse duplicate = reservationService.hold(roomId, request("req-1", 1L, START, END));

        assertThat(duplicate.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(timesBooked()).isZero();
    }

    @Test
    void concurrentHoldsOfSameSlotSucceedExactlyOnce() throws Exception {
        int attempts = 8;
        List<Callable<Boolean>> tasks = IntStream.range(0, attempts)
                .<Callable<Boolean>>mapToObj(i -> () -> tryHold(request("req-" + i, (long) i, START, END)))
                .toList();

        List<Boolean> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            results = executor.invokeAll(tasks).stream().map(RoomReservationServiceTest::await).toList();
        }

        assertThat(results).containsOnlyOnce(true);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(timesBooked()).isEqualTo(1);
    }

    private boolean tryHold(AvailabilityRequest request) {
        try {
            reservationService.hold(roomId, request);
            return true;
        } catch (ConflictException e) {
            return false;
        }
    }

    private long timesBooked() {
        return roomRepository.findById(roomId).orElseThrow().getTimesBooked();
    }

    private static AvailabilityRequest request(String requestId, Long bookingId, LocalDate start, LocalDate end) {
        return new AvailabilityRequest(requestId, bookingId, start, end);
    }

    private static Boolean await(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
