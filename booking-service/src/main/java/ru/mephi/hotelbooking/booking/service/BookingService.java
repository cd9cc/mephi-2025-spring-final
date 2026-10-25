package ru.mephi.hotelbooking.booking.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.mephi.hotelbooking.booking.dto.booking.BookingCreateRequest;
import ru.mephi.hotelbooking.booking.dto.booking.BookingResponse;

public interface BookingService {

    BookingResponse create(Long userId, BookingCreateRequest request);

    BookingResponse get(Long userId, Long bookingId);

    Page<BookingResponse> history(Long userId, Pageable pageable);

    BookingResponse cancel(Long userId, Long bookingId);
}
