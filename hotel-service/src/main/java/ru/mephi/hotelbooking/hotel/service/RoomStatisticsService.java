package ru.mephi.hotelbooking.hotel.service;

import java.util.List;
import ru.mephi.hotelbooking.hotel.dto.HotelOccupancyResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomOccupancyResponse;
import ru.mephi.hotelbooking.hotel.dto.StatisticsPeriod;

public interface RoomStatisticsService {

    List<RoomOccupancyResponse> occupancy(StatisticsPeriod period);

    List<HotelOccupancyResponse> hotelOccupancy(StatisticsPeriod period);
}
