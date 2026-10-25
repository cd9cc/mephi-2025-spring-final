package ru.mephi.hotelbooking.booking.exception;

import org.springframework.security.authentication.BadCredentialsException;

public class InvalidCredentialsException extends BadCredentialsException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
