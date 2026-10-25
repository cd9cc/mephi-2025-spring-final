package ru.mephi.hotelbooking.hotel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotNull Long hotelId,
        @NotBlank @Size(max = 20) String number,
        @NotNull @Min(1) @Max(20) Integer capacity,
        Boolean available
) {
}
