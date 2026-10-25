package ru.mephi.hotelbooking.booking.client;

public class HotelClientException extends RuntimeException {

    public HotelClientException(String message) {
        super(message);
    }

    public HotelClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
