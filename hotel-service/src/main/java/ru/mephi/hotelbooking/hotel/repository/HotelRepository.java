package ru.mephi.hotelbooking.hotel.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotelbooking.hotel.entity.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    @Query("""
            select h from Hotel h
            where (:city is null or lower(h.city) = lower(:city))
              and (:name is null or lower(h.name) like lower(concat('%', :name, '%')))
            """)
    Page<Hotel> search(@Param("city") String city, @Param("name") String name, Pageable pageable);
}
