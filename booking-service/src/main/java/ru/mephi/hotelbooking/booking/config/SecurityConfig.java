package ru.mephi.hotelbooking.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import ru.mephi.hotelbooking.common.security.JwtProperties;
import ru.mephi.hotelbooking.common.security.JwtSupport;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return JwtSupport.encoder(properties);
    }
}
