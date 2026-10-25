package ru.mephi.hotelbooking.gateway.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import ru.mephi.hotelbooking.common.security.JwtProperties;
import ru.mephi.hotelbooking.common.security.JwtSupport;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class GatewaySecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/bookings/user/register",
            "/api/bookings/user/auth",
            "/actuator/health/**",
            "/actuator/info",
            "/api/rooms/*/confirm-availability",
            "/api/rooms/*/release",
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         JsonAuthenticationEntryPoint entryPoint) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(server -> server
                        .jwt(withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(JwtSupport.secretKey(properties.secret()))
                .macAlgorithm(JwtSupport.ALGORITHM)
                .build();
        decoder.setJwtValidator(JwtSupport.validator(properties));
        return decoder;
    }
}
