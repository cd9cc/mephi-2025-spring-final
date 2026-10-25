package ru.mephi.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;
import ru.mephi.hotelbooking.hotel.entity.RoomReservation;

@Mapper
public interface ReservationMapper {

    @Mapping(target = "reservationId", source = "id")
    @Mapping(target = "roomId", source = "room.id")
    ReservationResponse toResponse(RoomReservation reservation);
}
