package ru.mephi.hotelbooking.hotel.dto;

public record HotelOccupancyResponse(
        Long hotelId,
        String name,
        String city,
        int rooms,
        int roomsInService,
        int idleRooms,
        long bookedNights,
        double occupancyRate,
        double averageTimesBooked
) {
}
