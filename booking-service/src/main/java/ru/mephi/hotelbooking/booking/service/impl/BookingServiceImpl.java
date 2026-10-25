package ru.mephi.hotelbooking.booking.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.booking.client.HotelClient;
import ru.mephi.hotelbooking.booking.client.dto.RoomCandidate;
import ru.mephi.hotelbooking.booking.config.BookingProperties;
import ru.mephi.hotelbooking.booking.dto.booking.BookingCreateRequest;
import ru.mephi.hotelbooking.booking.dto.booking.BookingResponse;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.mapper.BookingMapper;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;
import ru.mephi.hotelbooking.booking.service.BookingSaga;
import ru.mephi.hotelbooking.booking.service.BookingService;
import ru.mephi.hotelbooking.booking.service.BookingStateManager;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.common.exception.ServiceUnavailableException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingStateManager stateManager;
    private final BookingSaga saga;
    private final HotelClient hotelClient;
    private final BookingMapper bookingMapper;
    private final BookingProperties properties;

    @Override
    public BookingResponse create(Long userId, BookingCreateRequest request) {
        var existing = bookingRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            return replay(existing.get(), userId);
        }

        List<Long> candidates = candidateRooms(request);
        Booking booking;
        try {
            booking = stateManager.createPending(userId, candidates.getFirst(), request);
        } catch (DataIntegrityViolationException e) {
            Booking concurrent = bookingRepository.findByRequestId(request.requestId()).orElseThrow(() -> e);
            return replay(concurrent, userId);
        }
        return toResult(saga.execute(booking, candidates));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(Long userId, Long bookingId) {
        return bookingMapper.toResponse(findOwn(userId, bookingId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> history(Long userId, Pageable pageable) {
        return bookingRepository.findByUserId(userId, pageable).map(bookingMapper::toResponse);
    }

    @Override
    public BookingResponse cancel(Long userId, Long bookingId) {
        Booking booking = findOwn(userId, bookingId);
        return switch (booking.getStatus()) {
            case CANCELLED -> bookingMapper.toResponse(booking);
            case PENDING -> throw new ConflictException(
                    "Booking %d is being processed and cannot be cancelled yet".formatted(bookingId));
            case CONFIRMED -> bookingMapper.toResponse(saga.cancelConfirmed(booking));
        };
    }

    private List<Long> candidateRooms(BookingCreateRequest request) {
        if (!request.autoSelect()) {
            return List.of(request.roomId());
        }
        List<RoomCandidate> recommended;
        try {
            recommended = hotelClient.recommendRooms(request.startDate(), request.endDate());
        } catch (RuntimeException e) {
            log.warn("Room recommendation failed: {}", e.toString());
            throw new ServiceUnavailableException("Hotel service is unavailable, try again later");
        }
        if (recommended.isEmpty()) {
            throw noFreeRooms(request);
        }
        return recommended.stream()
                .map(RoomCandidate::id)
                .limit(properties.autoSelectMaxRooms())
                .toList();
    }

    private BookingResponse replay(Booking existing, Long userId) {
        if (!existing.getUser().getId().equals(userId)) {
            throw new ConflictException("requestId %s is already used".formatted(existing.getRequestId()));
        }
        log.info("Duplicate create request requestId={}: returning booking {} in status {}",
                existing.getRequestId(), existing.getId(), existing.getStatus());
        return toResult(existing);
    }

    private BookingResponse toResult(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            switch (booking.getCancelReason()) {
                case ROOM_UNAVAILABLE -> throw new ConflictException(
                        "Booking %d is cancelled: room is not available for the requested dates"
                                .formatted(booking.getId()));
                case HOTEL_UNAVAILABLE -> throw new ServiceUnavailableException(
                        "Booking %d is cancelled: hotel service is unavailable, retry with a new requestId"
                                .formatted(booking.getId()));
                case PROCESSING_TIMEOUT -> throw new ServiceUnavailableException(
                        "Booking %d is cancelled: processing was not completed in time, retry with a new requestId"
                                .formatted(booking.getId()));
                default -> log.debug("Booking {} was cancelled by the user", booking.getId());
            }
        }
        return bookingMapper.toResponse(booking);
    }

    private Booking findOwn(Long userId, Long bookingId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> NotFoundException.of("Booking", bookingId));
    }

    private static ConflictException noFreeRooms(BookingCreateRequest request) {
        return new ConflictException("No free rooms for [%s, %s)".formatted(request.startDate(), request.endDate()));
    }
}
