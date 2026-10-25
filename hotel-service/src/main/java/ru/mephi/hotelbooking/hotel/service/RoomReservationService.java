package ru.mephi.hotelbooking.hotel.service;

import java.util.Optional;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;

public interface RoomReservationService {

    ReservationResponse hold(Long roomId, AvailabilityRequest request);

    Optional<ReservationResponse> release(Long roomId, ReleaseRequest request);
}
