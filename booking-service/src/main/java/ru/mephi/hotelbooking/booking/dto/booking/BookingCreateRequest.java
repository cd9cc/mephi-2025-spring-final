package ru.mephi.hotelbooking.booking.dto.booking;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ru.mephi.hotelbooking.common.validation.DateRange;
import ru.mephi.hotelbooking.common.validation.ValidDateRange;

@ValidDateRange
public record BookingCreateRequest(
        @NotBlank @Size(max = 64) String requestId,
        Long roomId,
        boolean autoSelect,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate
) implements DateRange {

    @JsonIgnore
    @AssertTrue(message = "roomId is required when autoSelect is false")
    public boolean isRoomSpecified() {
        return autoSelect || roomId != null;
    }
}
