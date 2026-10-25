package ru.mephi.hotelbooking.gateway;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.mephi.hotelbooking.common.security.JwtClaims;
import ru.mephi.hotelbooking.common.security.JwtProperties;
import ru.mephi.hotelbooking.common.security.JwtSupport;
import ru.mephi.hotelbooking.common.trace.TraceContext;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private JwtProperties jwtProperties;

    @ParameterizedTest
    @ValueSource(strings = {"/api/rooms/1/confirm-availability", "/api/rooms/1/release"})
    void internalHotelEndpointsAreNotPublished(String path) {
        webTestClient.post().uri(path)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void requestWithoutTokenIsRejectedAtEntry() {
        webTestClient.get().uri("/api/hotels")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(TraceContext.TRACE_ID_HEADER)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.path").isEqualTo("/api/hotels")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void requestWithValidTokenIsRouted() {
        webTestClient.get().uri("/api/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void loginIsPublic() {
        webTestClient.post().uri("/api/bookings/user/auth")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void clientTraceIdIsKept() {
        webTestClient.get().uri("/api/hotels")
                .header(TraceContext.TRACE_ID_HEADER, "client-trace-1")
                .exchange()
                .expectHeader().valueEquals(TraceContext.TRACE_ID_HEADER, "client-trace-1")
                .expectBody().jsonPath("$.traceId").isEqualTo("client-trace-1");
    }

    private String userToken() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject("1")
                .claim(JwtClaims.ROLE, "USER")
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return JwtSupport.encoder(jwtProperties)
                .encode(JwtEncoderParameters.from(JwsHeader.with(JwtSupport.ALGORITHM).build(), claims))
                .getTokenValue();
    }
}
