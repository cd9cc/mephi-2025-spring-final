package ru.mephi.hotelbooking.hotel.service.impl;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.hotel.dto.HotelOccupancyResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomOccupancyResponse;
import ru.mephi.hotelbooking.hotel.dto.StatisticsPeriod;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomReservation;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomReservationRepository;
import ru.mephi.hotelbooking.hotel.service.RoomStatisticsService;

@Service
@RequiredArgsConstructor
public class RoomStatisticsServiceImpl implements RoomStatisticsService {

    private static final Comparator<RoomOccupancyResponse> ROOMS_MOST_LOADED_FIRST =
            Comparator.comparingDouble(RoomOccupancyResponse::occupancyRate).reversed()
                    .thenComparing(RoomOccupancyResponse::roomId);
    private static final Comparator<HotelOccupancyResponse> HOTELS_MOST_LOADED_FIRST =
            Comparator.comparingDouble(HotelOccupancyResponse::occupancyRate).reversed()
                    .thenComparing(HotelOccupancyResponse::hotelId);

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoomOccupancyResponse> occupancy(StatisticsPeriod period) {
        if (period.hotelId() != null) {
            requireHotel(period.hotelId());
        }
        Map<Long, Long> bookedNightsByRoom = bookedNightsByRoom(period);
        long periodNights = nights(period);

        return rooms(period.hotelId()).stream()
                .map(room -> toOccupancy(room, bookedNightsByRoom.getOrDefault(room.getId(), 0L), periodNights))
                .sorted(ROOMS_MOST_LOADED_FIRST)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotelOccupancyResponse> hotelOccupancy(StatisticsPeriod period) {
        List<Hotel> hotels = period.hotelId() == null
                ? hotelRepository.findAll(RoomServiceImpl.BY_ID)
                : List.of(requireHotel(period.hotelId()));
        Map<Long, List<Room>> roomsByHotel = rooms(period.hotelId()).stream()
                .collect(groupingBy(room -> room.getHotel().getId()));
        Map<Long, Long> bookedNightsByRoom = bookedNightsByRoom(period);
        long periodNights = nights(period);

        return hotels.stream()
                .map(hotel -> toHotelOccupancy(hotel, roomsByHotel.getOrDefault(hotel.getId(), List.of()),
                        bookedNightsByRoom, periodNights))
                .sorted(HOTELS_MOST_LOADED_FIRST)
                .toList();
    }

    private Hotel requireHotel(Long hotelId) {
        return hotelRepository.findById(hotelId).orElseThrow(() -> NotFoundException.of("Hotel", hotelId));
    }

    private List<Room> rooms(Long hotelId) {
        return hotelId == null
                ? roomRepository.findAll(RoomServiceImpl.BY_ID)
                : roomRepository.findByHotelId(hotelId, RoomServiceImpl.BY_ID);
    }

    private Map<Long, Long> bookedNightsByRoom(StatisticsPeriod period) {
        return reservationRepository.findActiveInPeriod(period.hotelId(), period.startDate(), period.endDate())
                .stream()
                .collect(groupingBy(reservation -> reservation.getRoom().getId(),
                        summingLong(reservation -> nightsWithinPeriod(reservation, period))));
    }

    private static RoomOccupancyResponse toOccupancy(Room room, long bookedNights, long periodNights) {
        return new RoomOccupancyResponse(
                room.getId(),
                room.getHotel().getId(),
                room.getNumber(),
                room.getTimesBooked(),
                bookedNights,
                (double) bookedNights / periodNights);
    }

    private static HotelOccupancyResponse toHotelOccupancy(Hotel hotel, List<Room> rooms,
                                                           Map<Long, Long> bookedNightsByRoom, long periodNights) {
        List<Room> inService = rooms.stream().filter(Room::isAvailable).toList();
        long bookedNights = inService.stream()
                .mapToLong(room -> bookedNightsByRoom.getOrDefault(room.getId(), 0L))
                .sum();
        int idleRooms = (int) inService.stream()
                .filter(room -> !bookedNightsByRoom.containsKey(room.getId()))
                .count();
        double occupancyRate = inService.isEmpty() ? 0 : (double) bookedNights / (inService.size() * periodNights);
        double averageTimesBooked = inService.stream().mapToLong(Room::getTimesBooked).average().orElse(0);
        return new HotelOccupancyResponse(hotel.getId(), hotel.getName(), hotel.getCity(), rooms.size(),
                inService.size(), idleRooms, bookedNights, occupancyRate, averageTimesBooked);
    }

    private static long nights(StatisticsPeriod period) {
        return ChronoUnit.DAYS.between(period.startDate(), period.endDate());
    }

    private static long nightsWithinPeriod(RoomReservation reservation, StatisticsPeriod period) {
        LocalDate from = max(reservation.getStartDate(), period.startDate());
        LocalDate to = min(reservation.getEndDate(), period.endDate());
        return ChronoUnit.DAYS.between(from, to);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
