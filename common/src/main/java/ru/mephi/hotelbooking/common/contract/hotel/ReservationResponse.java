package ru.mephi.hotelbooking.common.contract.hotel;

import java.time.LocalDate;

public record ReservationResponse(
        Long reservationId,
        Long roomId,
        Long bookingId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status
) {
}
