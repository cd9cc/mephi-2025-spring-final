package ru.mephi.hotelbooking.hotel.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import ru.mephi.hotelbooking.common.validation.DateRange;
import ru.mephi.hotelbooking.common.validation.ValidDateRange;

@ValidDateRange
public record RoomSearch(
        Long hotelId,
        LocalDate startDate,
        LocalDate endDate
) implements DateRange {

    @JsonIgnore
    @AssertTrue(message = "startDate and endDate must be specified together")
    public boolean isPeriodComplete() {
        return (startDate == null) == (endDate == null);
    }

    @JsonIgnore
    public boolean hasPeriod() {
        return startDate != null;
    }
}
