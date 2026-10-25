package ru.mephi.hotelbooking.booking.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotelbooking.booking.entity.Booking;
import ru.mephi.hotelbooking.booking.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByRequestId(String requestId);

    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    List<Booking> findByStatusAndUpdatedAtBefore(BookingStatus status, Instant updatedBefore, Limit limit);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<BookingStatus> statuses);

    @Modifying
    @Query("delete from Booking b where b.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
