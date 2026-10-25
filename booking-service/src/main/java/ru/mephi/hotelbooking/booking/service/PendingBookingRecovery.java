package ru.mephi.hotelbooking.booking.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.mephi.hotelbooking.booking.config.BookingProperties;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.repository.BookingRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingBookingRecovery {

    private static final Limit BATCH = Limit.of(100);

    private final BookingRepository bookingRepository;
    private final BookingSaga saga;
    private final BookingProperties properties;

    @Scheduled(fixedDelayString = "${booking.pending-recovery-interval}",
            initialDelayString = "${booking.pending-recovery-interval}")
    public void recoverStale() {
        recover(Instant.now().minus(properties.pendingTimeout()));
    }

    void recover(Instant updatedBefore) {
        List<Booking> stale =
                bookingRepository.findByStatusAndUpdatedAtBefore(BookingStatus.PENDING, updatedBefore, BATCH);
        if (!stale.isEmpty()) {
            log.warn("Found {} bookings stuck in PENDING since before {}, cancelling", stale.size(), updatedBefore);
        }
        for (Booking booking : stale) {
            try {
                saga.expire(booking);
            } catch (RuntimeException e) {
                log.warn("Booking {} recovery failed: {}", booking.getId(), e.toString());
            }
        }
    }
}
