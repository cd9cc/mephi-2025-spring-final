package ru.mephi.hotelbooking.hotel.dto;

public record RoomOccupancyResponse(
        Long roomId,
        Long hotelId,
        String number,
        long timesBooked,
        long bookedNights,
        double occupancyRate
) {
}
