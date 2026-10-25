package ru.mephi.hotelbooking.hotel.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotelbooking.hotel.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByHotelIdAndNumber(Long hotelId, String number);

    boolean existsByHotelIdAndNumberAndIdNot(Long hotelId, String number, Long id);

    List<Room> findByHotelId(Long hotelId, Sort sort);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select r from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
            """)
    List<Room> findAvailable(@Param("hotelId") Long hotelId, Sort sort);

    @Query("""
            select r from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (
                  select 1 from RoomReservation rr
                  where rr.room = r
                    and rr.status = ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus.ACTIVE
                    and rr.startDate < :endDate
                    and rr.endDate > :startDate)
            """)
    List<Room> findFree(
            @Param("hotelId") Long hotelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Sort sort);
}
