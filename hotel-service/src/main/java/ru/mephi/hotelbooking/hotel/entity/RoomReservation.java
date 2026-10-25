package ru.mephi.hotelbooking.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.mephi.hotelbooking.common.contract.hotel.ReservationStatus;

@Entity
@Table(
        name = "room_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_room_reservations_request_id", columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_room_reservations_room_dates", columnList = "room_id, start_date, end_date"),
                @Index(name = "idx_room_reservations_booking_id", columnList = "booking_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class RoomReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "fk_room_reservations_room"))
    private Room room;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
