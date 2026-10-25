package ru.mephi.hotelbooking.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.common.trace.TraceContext;
import ru.mephi.hotelbooking.hotel.service.RoomReservationService;

@Tag(name = "Internal", description = "Booking saga steps, service token with scope 'internal' only")
@RestController
@RequestMapping("/api/rooms/{id}")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SCOPE_internal')")
public class RoomReservationController {

    private final RoomReservationService reservationService;

    @Operation(summary = "Hold the room for the period; idempotent by requestId")
    @PostMapping("/confirm-availability")
    public ReservationResponse confirmAvailability(@PathVariable Long id,
                                                   @Valid @RequestBody AvailabilityRequest request) {
        try (var ignored = MDC.putCloseable(TraceContext.BOOKING_ID_MDC_KEY, request.bookingId().toString())) {
            return reservationService.hold(id, request);
        }
    }

    @Operation(summary = "Compensation: release the hold of the booking; 204 if there was no hold")
    @PostMapping("/release")
    public ResponseEntity<ReservationResponse> release(@PathVariable Long id,
                                                       @Valid @RequestBody ReleaseRequest request) {
        try (var ignored = MDC.putCloseable(TraceContext.BOOKING_ID_MDC_KEY, request.bookingId().toString())) {
            return reservationService.release(id, request)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        }
    }
}
