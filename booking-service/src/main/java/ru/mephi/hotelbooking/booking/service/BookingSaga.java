package ru.mephi.hotelbooking.booking.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import ru.mephi.hotelbooking.booking.client.HotelClient;
import ru.mephi.hotelbooking.booking.client.RoomUnavailableException;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.CancelReason;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.common.exception.ServiceUnavailableException;
import ru.mephi.hotelbooking.common.trace.TraceContext;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingSaga {

    private final HotelClient hotelClient;
    private final BookingStateManager stateManager;

    public Booking execute(Booking booking, List<Long> candidateRoomIds) {
        try (var ignored = MDC.putCloseable(TraceContext.BOOKING_ID_MDC_KEY, booking.getId().toString())) {
            for (Long roomId : candidateRoomIds) {
                if (!roomId.equals(booking.getRoomId())) {
                    booking = stateManager.changeRoom(booking.getId(), roomId);
                }
                HoldResult result = hold(booking);
                if (result == HoldResult.HELD) {
                    return confirm(booking);
                }
                if (result == HoldResult.FAILED) {
                    compensate(booking);
                    return stateManager.cancel(booking.getId(), CancelReason.HOTEL_UNAVAILABLE);
                }
            }
            return stateManager.cancel(booking.getId(), CancelReason.ROOM_UNAVAILABLE);
        }
    }

    public Booking cancelConfirmed(Booking booking) {
        try (var ignored = MDC.putCloseable(TraceContext.BOOKING_ID_MDC_KEY, booking.getId().toString())) {
            try {
                hotelClient.release(booking.getRoomId(), releaseRequest(booking));
            } catch (RuntimeException e) {
                log.warn("Booking {} release of room {} failed: {}",
                        booking.getId(), booking.getRoomId(), e.toString());
                throw new ServiceUnavailableException(
                        "Hotel service is unavailable, booking %d is not cancelled".formatted(booking.getId()));
            }
            return stateManager.cancel(booking.getId(), CancelReason.USER_CANCELLED);
        }
    }

    public void expire(Booking booking) {
        try (var ignored = MDC.putCloseable(TraceContext.BOOKING_ID_MDC_KEY, booking.getId().toString())) {
            stateManager.expire(booking.getId()).ifPresent(this::compensate);
        }
    }

    private HoldResult hold(Booking booking) {
        Long roomId = booking.getRoomId();
        AvailabilityRequest request = new AvailabilityRequest(
                "booking-%d-room-%d".formatted(booking.getId(), roomId),
                booking.getId(), booking.getStartDate(), booking.getEndDate());
        log.info("Booking {} confirming availability of room {}, requestId={}",
                booking.getId(), roomId, request.requestId());
        try {
            ReservationResponse reservation = hotelClient.hold(roomId, request);
            if (reservation.status() == ReservationStatus.ACTIVE) {
                log.info("Booking {} room {} held, reservation {}",
                        booking.getId(), roomId, reservation.reservationId());
                return HoldResult.HELD;
            }
            log.info("Booking {} hold of room {} was already released", booking.getId(), roomId);
            return HoldResult.ROOM_UNAVAILABLE;
        } catch (RoomUnavailableException e) {
            log.info("Booking {} room {} is unavailable: {}", booking.getId(), roomId, e.getMessage());
            return HoldResult.ROOM_UNAVAILABLE;
        } catch (RuntimeException e) {
            log.warn("Booking {} availability confirmation of room {} failed: {}",
                    booking.getId(), roomId, e.toString());
            return HoldResult.FAILED;
        }
    }

    private Booking confirm(Booking booking) {
        try {
            return stateManager.confirm(booking.getId());
        } catch (RuntimeException e) {
            log.error("Booking {} cannot be confirmed locally, compensating", booking.getId(), e);
            compensate(booking);
            throw e;
        }
    }

    private void compensate(Booking booking) {
        log.info("Booking {} compensation: releasing room {}", booking.getId(), booking.getRoomId());
        try {
            hotelClient.release(booking.getRoomId(), releaseRequest(booking));
            log.info("Booking {} compensation succeeded: room {} released", booking.getId(), booking.getRoomId());
        } catch (RuntimeException e) {
            log.error("Booking {} compensation failed: hold of room {} may remain in hotel service: {}",
                    booking.getId(), booking.getRoomId(), e.toString());
        }
    }

    private static ReleaseRequest releaseRequest(Booking booking) {
        return new ReleaseRequest("booking-%d-release".formatted(booking.getId()), booking.getId());
    }

    private enum HoldResult {
        HELD,
        ROOM_UNAVAILABLE,
        FAILED
    }
}
