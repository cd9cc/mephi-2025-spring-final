package ru.mephi.hotelbooking.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("hotel-booking") String issuer,
        @DefaultValue("1h") Duration ttl
) {
}
