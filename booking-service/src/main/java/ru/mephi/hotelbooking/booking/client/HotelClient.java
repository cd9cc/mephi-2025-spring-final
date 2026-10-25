package ru.mephi.hotelbooking.booking.client;

import java.time.LocalDate;
import java.util.List;
import ru.mephi.hotelbooking.booking.client.dto.RoomCandidate;
import ru.mephi.hotelbooking.common.contract.hotel.AvailabilityRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReleaseRequest;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationResponse;

public interface HotelClient {

    List<RoomCandidate> recommendRooms(LocalDate startDate, LocalDate endDate);

    ReservationResponse hold(Long roomId, AvailabilityRequest request);

    void release(Long roomId, ReleaseRequest request);
}
