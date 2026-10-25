package ru.mephi.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.booking.dto.booking.BookingCreateRequest;
import ru.mephi.hotelbooking.booking.dto.booking.BookingResponse;
import ru.mephi.hotelbooking.booking.security.CurrentUserId;
import ru.mephi.hotelbooking.booking.service.BookingService;
import ru.mephi.hotelbooking.common.dto.PageResponse;

@Tag(name = "Bookings")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Create a booking for a given room or with autoSelect; idempotent by requestId")
    @PostMapping("/booking")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Parameter(hidden = true) @CurrentUserId Long userId,
                                  @Valid @RequestBody BookingCreateRequest request) {
        return bookingService.create(userId, request);
    }

    @Operation(summary = "Booking history of the current user, newest first by default")
    @GetMapping
    public PageResponse<BookingResponse> history(
            @Parameter(hidden = true) @CurrentUserId Long userId,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(bookingService.history(userId, pageable));
    }

    @Operation(summary = "Get an own booking")
    @GetMapping("/booking/{id}")
    public BookingResponse get(@Parameter(hidden = true) @CurrentUserId Long userId, @PathVariable Long id) {
        return bookingService.get(userId, id);
    }

    @Operation(summary = "Cancel an own booking and release the room")
    @DeleteMapping("/booking/{id}")
    public BookingResponse cancel(@Parameter(hidden = true) @CurrentUserId Long userId, @PathVariable Long id) {
        return bookingService.cancel(userId, id);
    }
}
