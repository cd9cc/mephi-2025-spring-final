package ru.mephi.hotelbooking.hotel.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.mephi.hotelbooking.hotel.dto.HotelFilter;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.dto.HotelResponse;

public interface HotelService {

    HotelResponse create(HotelRequest request);

    HotelResponse update(Long id, HotelRequest request);

    void delete(Long id);

    HotelResponse get(Long id);

    Page<HotelResponse> search(HotelFilter filter, Pageable pageable);
}
