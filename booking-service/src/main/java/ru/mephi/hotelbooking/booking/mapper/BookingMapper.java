package ru.mephi.hotelbooking.booking.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.mephi.hotelbooking.booking.dto.booking.BookingResponse;
import ru.mephi.hotelbooking.booking.entity.Booking;

@Mapper
public interface BookingMapper {

    @Mapping(target = "userId", source = "user.id")
    BookingResponse toResponse(Booking booking);
}
