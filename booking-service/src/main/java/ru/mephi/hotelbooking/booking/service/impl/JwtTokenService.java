package ru.mephi.hotelbooking.booking.service.impl;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import ru.mephi.hotelbooking.booking.dto.auth.TokenResponse;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.service.TokenService;
import ru.mephi.hotelbooking.common.security.JwtClaims;
import ru.mephi.hotelbooking.common.security.JwtProperties;
import ru.mephi.hotelbooking.common.security.JwtSupport;

@Service
public class JwtTokenService implements TokenService {

    static final Duration SERVICE_TOKEN_TTL = Duration.ofMinutes(5);

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final String serviceName;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties,
                           @Value("${spring.application.name}") String serviceName) {
        this.encoder = encoder;
        this.properties = properties;
        this.serviceName = serviceName;
    }

    @Override
    public TokenResponse issue(UserResponse user) {
        JwtClaimsSet claims = claims(user.id().toString(), properties.ttl())
                .claim(JwtClaims.USERNAME, user.username())
                .claim(JwtClaims.ROLE, user.role().name())
                .build();
        return TokenResponse.bearer(encode(claims), properties.ttl().toSeconds());
    }

    @Override
    public String issueServiceToken() {
        JwtClaimsSet claims = claims(serviceName, SERVICE_TOKEN_TTL)
                .claim(JwtClaims.SCOPE, JwtClaims.INTERNAL_SCOPE)
                .build();
        return encode(claims);
    }

    private JwtClaimsSet.Builder claims(String subject, Duration ttl) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(ttl));
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(JwtSupport.ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
