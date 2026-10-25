package ru.mephi.hotelbooking.hotel.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotelbooking.hotel.entity.RoomReservation;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    Optional<RoomReservation> findByRequestId(String requestId);

    List<RoomReservation> findByRoomIdAndBookingId(Long roomId, Long bookingId);

    boolean existsByRoomId(Long roomId);

    boolean existsByRoomHotelId(Long hotelId);

    @Query("""
            select count(rr) > 0 from RoomReservation rr
            where rr.room.id = :roomId
              and rr.status = ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus.ACTIVE
              and rr.startDate < :endDate
              and rr.endDate > :startDate
            """)
    boolean existsActiveOverlapping(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            select rr from RoomReservation rr
            join fetch rr.room r
            where rr.status = ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus.ACTIVE
              and rr.startDate < :to
              and rr.endDate > :from
              and (:hotelId is null or r.hotel.id = :hotelId)
            """)
    List<RoomReservation> findActiveInPeriod(
            @Param("hotelId") Long hotelId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
