package ru.mephi.hotelbooking.booking.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.booking.dto.booking.BookingCreateRequest;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.CancelReason;
import ru.mephi.hotelbooking.booking.entity.User;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.repository.UserRepository;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.NotFoundException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingStateManager {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public Booking createPending(Long userId, Long roomId, BookingCreateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoomId(roomId);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setRequestId(request.requestId());
        booking.setStatus(BookingStatus.PENDING);
        bookingRepository.saveAndFlush(booking);
        log.info("Booking {} created: status=PENDING, user={}, room={}, period=[{}, {}), requestId={}",
                booking.getId(), userId, roomId, request.startDate(), request.endDate(), request.requestId());
        return booking;
    }

    @Transactional
    public Booking changeRoom(Long bookingId, Long roomId) {
        Booking booking = find(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException(
                    "Booking %d is %s, room cannot be changed".formatted(bookingId, booking.getStatus()));
        }
        log.info("Booking {} room changed {} -> {}", bookingId, booking.getRoomId(), roomId);
        booking.setRoomId(roomId);
        return booking;
    }

    @Transactional
    public Booking confirm(Long bookingId) {
        Booking booking = find(bookingId);
        booking.confirm();
        log.info("Booking {} status PENDING -> CONFIRMED, room={}", bookingId, booking.getRoomId());
        return booking;
    }

    @Transactional
    public Booking cancel(Long bookingId, CancelReason reason) {
        Booking booking = find(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;
        }
        BookingStatus from = booking.getStatus();
        booking.cancel(reason);
        log.info("Booking {} status {} -> CANCELLED, reason={}, room={}", bookingId, from, reason, booking.getRoomId());
        return booking;
    }

    @Transactional
    public Optional<Booking> expire(Long bookingId) {
        Booking booking = find(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            return Optional.empty();
        }
        booking.cancel(CancelReason.PROCESSING_TIMEOUT);
        log.info("Booking {} status PENDING -> CANCELLED, reason={}, room={}",
                bookingId, CancelReason.PROCESSING_TIMEOUT, booking.getRoomId());
        return Optional.of(booking);
    }

    private Booking find(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow(() -> NotFoundException.of("Booking", bookingId));
    }
}
