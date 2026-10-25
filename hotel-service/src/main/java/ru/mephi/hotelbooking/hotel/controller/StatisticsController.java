package ru.mephi.hotelbooking.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.hotel.dto.HotelOccupancyResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomOccupancyResponse;
import ru.mephi.hotelbooking.hotel.dto.StatisticsPeriod;
import ru.mephi.hotelbooking.hotel.service.RoomStatisticsService;

@Tag(name = "Statistics")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final RoomStatisticsService statisticsService;

    @Operation(summary = "Room load for a period [startDate, endDate), most loaded first (ADMIN)")
    @GetMapping("/api/rooms/statistics")
    public List<RoomOccupancyResponse> roomOccupancy(@Valid @ParameterObject StatisticsPeriod period) {
        return statisticsService.occupancy(period);
    }

    @Operation(summary = "Hotel load for a period: occupancy rate, idle rooms, average times_booked (ADMIN)")
    @GetMapping("/api/hotels/statistics")
    public List<HotelOccupancyResponse> hotelOccupancy(@Valid @ParameterObject StatisticsPeriod period) {
        return statisticsService.hotelOccupancy(period);
    }
}
