package ru.mephi.hotelbooking.hotel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomResponse;
import ru.mephi.hotelbooking.hotel.entity.Room;

@Mapper
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    RoomResponse toResponse(Room room);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "available", defaultValue = "true")
    Room toEntity(RoomRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "timesBooked", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "available", defaultValue = "true")
    void update(@MappingTarget Room room, RoomRequest request);
}
