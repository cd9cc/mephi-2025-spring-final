package ru.mephi.hotelbooking.booking.mapper;

import org.mapstruct.Mapper;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.entity.User;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
