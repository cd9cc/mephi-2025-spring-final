package ru.mephi.hotelbooking.hotel.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.hotel.dto.HotelFilter;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.dto.HotelResponse;
import ru.mephi.hotelbooking.hotel.entity.Hotel;
import ru.mephi.hotelbooking.hotel.mapper.HotelMapper;
import ru.mephi.hotelbooking.hotel.repository.HotelRepository;
import ru.mephi.hotelbooking.hotel.repository.RoomReservationRepository;
import ru.mephi.hotelbooking.hotel.service.HotelService;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final RoomReservationRepository reservationRepository;
    private final HotelMapper hotelMapper;

    @Override
    @Transactional
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = hotelRepository.save(hotelMapper.toEntity(request));
        log.info("Hotel {} created", hotel.getId());
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional
    public HotelResponse update(Long id, HotelRequest request) {
        Hotel hotel = findHotel(id);
        hotelMapper.update(hotel, request);
        log.info("Hotel {} updated", id);
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Hotel hotel = findHotel(id);
        if (reservationRepository.existsByRoomHotelId(id)) {
            throw new ConflictException("Hotel %d has room reservations and cannot be deleted".formatted(id));
        }
        hotelRepository.delete(hotel);
        log.info("Hotel {} deleted", id);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelResponse get(Long id) {
        return hotelMapper.toResponse(findHotel(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HotelResponse> search(HotelFilter filter, Pageable pageable) {
        return hotelRepository.search(filter.city(), filter.name(), pageable).map(hotelMapper::toResponse);
    }

    private Hotel findHotel(Long id) {
        return hotelRepository.findById(id).orElseThrow(() -> NotFoundException.of("Hotel", id));
    }
}
