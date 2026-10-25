package ru.mephi.hotelbooking.booking.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
