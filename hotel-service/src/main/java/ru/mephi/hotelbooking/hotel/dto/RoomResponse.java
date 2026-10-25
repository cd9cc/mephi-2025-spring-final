package ru.mephi.hotelbooking.hotel.dto;

public record RoomResponse(
        Long id,
        Long hotelId,
        String number,
        int capacity,
        boolean available,
        long timesBooked
) {
}
