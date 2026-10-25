package ru.mephi.hotelbooking.booking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hotel-client")
public record HotelClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
