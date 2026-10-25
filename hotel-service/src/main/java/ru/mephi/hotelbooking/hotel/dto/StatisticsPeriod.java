package ru.mephi.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import ru.mephi.hotelbooking.common.validation.DateRange;
import ru.mephi.hotelbooking.common.validation.ValidDateRange;

@ValidDateRange
public record StatisticsPeriod(
        Long hotelId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) implements DateRange {
}
