package ru.mephi.hotelbooking.booking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("booking")
public record BookingProperties(int autoSelectMaxRooms, Duration pendingTimeout) {
}
