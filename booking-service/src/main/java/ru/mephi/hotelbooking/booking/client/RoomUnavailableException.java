package ru.mephi.hotelbooking.booking.client;

public class RoomUnavailableException extends HotelClientException {

    public RoomUnavailableException(String message) {
        super(message);
    }
}
