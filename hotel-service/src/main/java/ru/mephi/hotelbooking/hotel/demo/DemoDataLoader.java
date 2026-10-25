package ru.mephi.hotelbooking.hotel.demo;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomReservation;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomReservationRepository;

@Slf4j
@Component
@ConditionalOnProperty(name = "demo-data.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hotelRepository.count() > 0) {
            return;
        }
        Hotel arbat = hotel("Арбат Плаза", "ул. Арбат, 10", "Москва");
        Room arbat101 = room(arbat, "101", 2, 5, true);
        Room arbat102 = room(arbat, "102", 2, 1, true);
        room(arbat, "103", 3, 0, true);
        Room arbat201 = room(arbat, "201", 4, 3, true);
        room(arbat, "202", 2, 0, false);

        Hotel tverskaya = hotel("Тверская Резиденция", "ул. Тверская, 5", "Москва");
        room(tverskaya, "11", 2, 2, true);
        room(tverskaya, "12", 2, 0, true);

        Hotel nevsky = hotel("Невский Берег", "Невский пр., 100", "Санкт-Петербург");
        room(nevsky, "1", 2, 4, true);
        room(nevsky, "2", 2, 1, true);

        LocalDate today = LocalDate.now();
        reservation(arbat101, 1L, today.plusDays(1), today.plusDays(4), ReservationStatus.ACTIVE);
        reservation(arbat201, 2L, today.minusDays(10), today.minusDays(7), ReservationStatus.ACTIVE);
        reservation(arbat102, 3L, today.plusDays(5), today.plusDays(7), ReservationStatus.RELEASED);

        log.info("Demo data loaded: {} hotels, {} rooms, {} reservations",
                hotelRepository.count(), roomRepository.count(), reservationRepository.count());
    }

    private Hotel hotel(String name, String address, String city) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setAddress(address);
        hotel.setCity(city);
        return hotelRepository.save(hotel);
    }

    private Room room(Hotel hotel, String number, int capacity, long timesBooked, boolean available) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setCapacity(capacity);
        room.setTimesBooked(timesBooked);
        room.setAvailable(available);
        return roomRepository.save(room);
    }

    private void reservation(Room room, Long bookingId, LocalDate start, LocalDate end, ReservationStatus status) {
        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(bookingId);
        reservation.setRequestId("booking-%d-room-%d".formatted(bookingId, room.getId()));
        reservation.setStartDate(start);
        reservation.setEndDate(end);
        reservation.setStatus(status);
        reservationRepository.save(reservation);
    }
}
