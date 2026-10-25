package ru.mephi.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String address,
        @Size(max = 100) String city
) {
}
