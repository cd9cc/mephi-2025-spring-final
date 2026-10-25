package ru.mephi.hotelbooking.common.contract.hotel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ru.mephi.hotelbooking.common.validation.DateRange;
import ru.mephi.hotelbooking.common.validation.ValidDateRange;

@ValidDateRange
public record AvailabilityRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotNull Long bookingId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) implements DateRange {
}
