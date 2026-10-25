package ru.mephi.hotelbooking.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class JwtSupport {

    public static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    private static final int MIN_SECRET_BYTES = 32;

    private JwtSupport() {
    }

    public static SecretKey secretKey(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least %d bytes".formatted(MIN_SECRET_BYTES));
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    public static JwtDecoder decoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties.secret()))
                .macAlgorithm(ALGORITHM)
                .build();
        decoder.setJwtValidator(validator(properties));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validator(JwtProperties properties) {
        return JwtValidators.createDefaultWithIssuer(properties.issuer());
    }

    public static JwtEncoder encoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties.secret())));
    }
}
