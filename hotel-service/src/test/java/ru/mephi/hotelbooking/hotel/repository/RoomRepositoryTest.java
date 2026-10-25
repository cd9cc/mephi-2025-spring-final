package ru.mephi.hotelbooking.hotel.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomReservation;

@DataJpaTest(properties = "eureka.client.enabled=false")
class RoomRepositoryTest {

    private static final LocalDate START = LocalDate.of(2030, 5, 10);
    private static final LocalDate END = START.plusDays(3);

    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomReservationRepository reservationRepository;

    private Hotel hotel;

    @BeforeEach
    void setUp() {
        hotel = new Hotel();
        hotel.setName("Test");
        hotel.setAddress("Street 1");
        hotelRepository.save(hotel);
    }

    @Test
    void findFreeExcludesRoomsWithOverlappingActiveReservation() {
        Room busy = room("101", 0);
        Room free = room("102", 0);
        reserve(busy, START.plusDays(1), END.plusDays(1), ReservationStatus.ACTIVE);

        List<Room> rooms = roomRepository.findFree(hotel.getId(), START, END, Sort.by("id"));

        assertThat(rooms).extracting(Room::getId).containsExactly(free.getId());
    }

    @Test
    void findFreeTreatsCheckOutDayAsFree() {
        Room room = room("101", 0);
        reserve(room, START.minusDays(2), START, ReservationStatus.ACTIVE);
        reserve(room, END, END.plusDays(2), ReservationStatus.ACTIVE);

        List<Room> rooms = roomRepository.findFree(hotel.getId(), START, END, Sort.by("id"));

        assertThat(rooms).extracting(Room::getId).containsExactly(room.getId());
    }

    @Test
    void findFreeIgnoresReleasedReservationsAndRoomsOutOfService() {
        Room released = room("101", 0);
        reserve(released, START, END, ReservationStatus.RELEASED);
        Room outOfService = room("102", 0);
        outOfService.setAvailable(false);

        List<Room> rooms = roomRepository.findFree(hotel.getId(), START, END, Sort.by("id"));

        assertThat(rooms).extracting(Room::getId).containsExactly(released.getId());
    }

    @Test
    void findFreeSortsByTimesBookedThenById() {
        Room loaded = room("101", 5);
        Room firstIdle = room("102", 1);
        Room secondIdle = room("103", 1);

        List<Room> rooms = roomRepository.findFree(null, START, END, Sort.by("timesBooked", "id"));

        assertThat(rooms).extracting(Room::getId)
                .containsExactly(firstIdle.getId(), secondIdle.getId(), loaded.getId());
    }

    private Room room(String number, long timesBooked) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setCapacity(2);
        room.setTimesBooked(timesBooked);
        return roomRepository.save(room);
    }

    private void reserve(Room room, LocalDate start, LocalDate end, ReservationStatus status) {
        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(1L);
        reservation.setRequestId("req-" + room.getId() + "-" + start);
        reservation.setStartDate(start);
        reservation.setEndDate(end);
        reservation.setStatus(status);
        reservationRepository.save(reservation);
    }
}
