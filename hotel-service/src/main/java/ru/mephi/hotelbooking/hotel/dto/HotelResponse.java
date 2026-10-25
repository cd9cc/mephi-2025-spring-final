package ru.mephi.hotelbooking.hotel.dto;

import java.time.Instant;

public record HotelResponse(
        Long id,
        String name,
        String address,
        String city,
        Instant createdAt
) {
}
