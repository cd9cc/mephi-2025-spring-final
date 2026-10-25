package ru.mephi.hotelbooking.hotel.service.impl;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomReservation;
import ru.mephi.hotelbooking.hotel.mapper.ReservationMapper;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomReservationRepository;
import ru.mephi.hotelbooking.hotel.service.RoomReservationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomReservationServiceImpl implements RoomReservationService {

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;

    @Override
    @Transactional
    public ReservationResponse hold(Long roomId, AvailabilityRequest request) {
        Room room = lockRoom(roomId);

        Optional<RoomReservation> existing = reservationRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            return replay(existing.get(), roomId, request);
        }
        if (!room.isAvailable()) {
            throw new ConflictException("Room %d is out of service".formatted(roomId));
        }
        if (reservationRepository.existsActiveOverlapping(roomId, request.startDate(), request.endDate())) {
            log.info("Room {} is busy for [{}, {}), booking {} rejected",
                    roomId, request.startDate(), request.endDate(), request.bookingId());
            throw new ConflictException("Room %d is already reserved for [%s, %s)"
                    .formatted(roomId, request.startDate(), request.endDate()));
        }

        RoomReservation reservation = reservationRepository.save(newReservation(room, request));
        room.setTimesBooked(room.getTimesBooked() + 1);
        log.info("Room {} held for booking {} [{}, {}), reservation {}, requestId={}, timesBooked={}",
                roomId, request.bookingId(), request.startDate(), request.endDate(),
                reservation.getId(), request.requestId(), room.getTimesBooked());
        return reservationMapper.toResponse(reservation);
    }

    @Override
    @Transactional
    public Optional<ReservationResponse> release(Long roomId, ReleaseRequest request) {
        Room room = lockRoom(roomId);

        List<RoomReservation> reservations =
                reservationRepository.findByRoomIdAndBookingId(roomId, request.bookingId());
        if (reservations.isEmpty()) {
            log.info("Room {} has no hold for booking {}, nothing to release, requestId={}",
                    roomId, request.bookingId(), request.requestId());
            return Optional.empty();
        }
        reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE)
                .forEach(reservation -> releaseHold(room, reservation, request));
        return Optional.of(reservationMapper.toResponse(reservations.getFirst()));
    }

    private ReservationResponse replay(RoomReservation existing, Long roomId, AvailabilityRequest request) {
        boolean sameOperation = existing.getRoom().getId().equals(roomId)
                && existing.getBookingId().equals(request.bookingId());
        if (!sameOperation) {
            throw new ConflictException("requestId %s is already used by another reservation"
                    .formatted(request.requestId()));
        }
        log.info("Duplicate hold requestId={} for booking {}: returning reservation {} in status {}",
                request.requestId(), request.bookingId(), existing.getId(), existing.getStatus());
        return reservationMapper.toResponse(existing);
    }

    private void releaseHold(Room room, RoomReservation reservation, ReleaseRequest request) {
        reservation.setStatus(ReservationStatus.RELEASED);
        room.setTimesBooked(Math.max(0, room.getTimesBooked() - 1));
        log.info("Room {} released for booking {}, reservation {}, requestId={}, timesBooked={}",
                room.getId(), reservation.getBookingId(), reservation.getId(), request.requestId(),
                room.getTimesBooked());
    }

    private Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> NotFoundException.of("Room", roomId));
    }

    private static RoomReservation newReservation(Room room, AvailabilityRequest request) {
        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(request.bookingId());
        reservation.setRequestId(request.requestId());
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservation;
    }
}
