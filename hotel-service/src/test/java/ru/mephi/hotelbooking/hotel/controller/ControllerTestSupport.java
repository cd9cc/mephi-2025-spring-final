package ru.mephi.hotelbooking.hotel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;
import ru.mephi.hotelbooking.common.security.JwtClaims;
import ru.mephi.hotelbooking.common.security.JwtProperties;
import ru.mephi.hotelbooking.common.security.JwtSupport;
import ru.mephi.hotelbooking.common.security.Role;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.entity.Room;
import ru.mephi.hotelbooking.hotel.entity.RoomReservation;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomReservationRepository;

@SpringBootTest
@AutoConfigureMockMvc
abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected HotelRepository hotelRepository;
    @Autowired
    protected RoomRepository roomRepository;
    @Autowired
    protected RoomReservationRepository reservationRepository;
    @Autowired
    private JwtProperties jwtProperties;

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    protected String userToken() {
        return bearer(claims("1").claim(JwtClaims.ROLE, Role.USER.name()));
    }

    protected String adminToken() {
        return bearer(claims("2").claim(JwtClaims.ROLE, Role.ADMIN.name()));
    }

    protected String serviceToken() {
        return bearer(claims("booking-service").claim(JwtClaims.SCOPE, JwtClaims.INTERNAL_SCOPE));
    }

    protected String adminTokenSignedWith(String secret) {
        var signer = new JwtProperties(secret, jwtProperties.issuer(), jwtProperties.ttl());
        return bearer(signer, claims("2").claim(JwtClaims.ROLE, Role.ADMIN.name()));
    }

    protected String expiredAdminToken() {
        Instant issuedAt = Instant.now().minus(2, ChronoUnit.HOURS);
        return bearer(claims("2")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(1, ChronoUnit.HOURS))
                .claim(JwtClaims.ROLE, Role.ADMIN.name()));
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected Hotel hotel(String name, String city) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setAddress("Street 1");
        hotel.setCity(city);
        return hotelRepository.save(hotel);
    }

    protected Room room(Hotel hotel, String number, long timesBooked) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setCapacity(2);
        room.setTimesBooked(timesBooked);
        return roomRepository.save(room);
    }

    protected void reserve(Room room, LocalDate start, LocalDate end) {
        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(1L);
        reservation.setRequestId("req-%d-%s".formatted(room.getId(), start));
        reservation.setStartDate(start);
        reservation.setEndDate(end);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservationRepository.save(reservation);
    }

    private JwtClaimsSet.Builder claims(String subject) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS));
    }

    private String bearer(JwtClaimsSet.Builder claims) {
        return bearer(jwtProperties, claims);
    }

    private static String bearer(JwtProperties signer, JwtClaimsSet.Builder claims) {
        JwsHeader header = JwsHeader.with(JwtSupport.ALGORITHM).build();
        String token = JwtSupport.encoder(signer)
                .encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
        return "Bearer " + token;
    }
}
