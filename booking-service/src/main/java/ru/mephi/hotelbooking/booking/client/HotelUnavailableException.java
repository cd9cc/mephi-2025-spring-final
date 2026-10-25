package ru.mephi.hotelbooking.booking.client;

public class HotelUnavailableException extends HotelClientException {

    public HotelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
