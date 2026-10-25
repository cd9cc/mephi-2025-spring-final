package ru.mephi.hotelbooking.booking.client.dto;

public record RoomCandidate(
        Long id,
        Long hotelId,
        String number,
        long timesBooked
) {
}
