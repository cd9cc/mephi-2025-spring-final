package ru.mephi.hotelbooking.hotel.service;

import java.util.List;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomSearch;

public interface RoomService {

    RoomResponse create(RoomRequest request);

    RoomResponse update(Long id, RoomRequest request);

    void delete(Long id);

    RoomResponse get(Long id);

    List<RoomResponse> findFree(RoomSearch search);

    List<RoomResponse> recommend(RoomSearch search);
}
