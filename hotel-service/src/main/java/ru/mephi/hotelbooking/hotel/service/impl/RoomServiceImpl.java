package ru.mephi.hotelbooking.hotel.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomSearch;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.mapper.RoomMapper;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomReservationRepository;
import ru.mephi.hotelbooking.hotel.service.RoomService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    static final Sort BY_ID = Sort.by("id");
    static final Sort LEAST_BOOKED_FIRST = Sort.by("timesBooked", "id");

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomReservationRepository reservationRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional
    public RoomResponse create(RoomRequest request) {
        Hotel hotel = findHotel(request.hotelId());
        if (roomRepository.existsByHotelIdAndNumber(hotel.getId(), request.number())) {
            throw duplicateNumber(request);
        }
        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        roomRepository.save(room);
        log.info("Room {} ({}) created in hotel {}", room.getId(), room.getNumber(), hotel.getId());
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = findRoom(id);
        Hotel hotel = findHotel(request.hotelId());
        if (roomRepository.existsByHotelIdAndNumberAndIdNot(hotel.getId(), request.number(), id)) {
            throw duplicateNumber(request);
        }
        roomMapper.update(room, request);
        room.setHotel(hotel);
        log.info("Room {} updated", id);
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Room room = findRoom(id);
        if (reservationRepository.existsByRoomId(id)) {
            throw new ConflictException(
                    "Room %d has reservations and cannot be deleted; set available=false instead".formatted(id));
        }
        roomRepository.delete(room);
        log.info("Room {} deleted", id);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse get(Long id) {
        return roomMapper.toResponse(findRoom(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> findFree(RoomSearch search) {
        return find(search, BY_ID);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> recommend(RoomSearch search) {
        return find(search, LEAST_BOOKED_FIRST);
    }

    private List<RoomResponse> find(RoomSearch search, Sort sort) {
        List<Room> rooms = search.hasPeriod()
                ? roomRepository.findFree(search.hotelId(), search.startDate(), search.endDate(), sort)
                : roomRepository.findAvailable(search.hotelId(), sort);
        return rooms.stream().map(roomMapper::toResponse).toList();
    }

    private Room findRoom(Long id) {
        return roomRepository.findById(id).orElseThrow(() -> NotFoundException.of("Room", id));
    }

    private Hotel findHotel(Long id) {
        return hotelRepository.findById(id).orElseThrow(() -> NotFoundException.of("Hotel", id));
    }

    private static ConflictException duplicateNumber(RoomRequest request) {
        return new ConflictException(
                "Room %s already exists in hotel %d".formatted(request.number(), request.hotelId()));
    }
}
