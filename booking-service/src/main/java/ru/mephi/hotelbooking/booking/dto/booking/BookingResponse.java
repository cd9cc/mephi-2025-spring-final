package ru.mephi.hotelbooking.booking.dto.booking;

import java.time.Instant;
import java.time.LocalDate;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;
import ru.mephi.hotelbooking.booking.entity.CancelReason;

public record BookingResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        CancelReason cancelReason,
        String requestId,
        Instant createdAt
) {
}
