package ru.mephi.hotelbooking.booking.entity;

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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.mephi.hotelbooking.common.exception.ConflictException;

@Entity
@Table(
        name = "bookings",
        uniqueConstraints = @UniqueConstraint(name = "uk_bookings_request_id", columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_bookings_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_bookings_room_dates", columnList = "room_id, start_date, end_date")
        })
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bookings_user"))
    private User user;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void confirm() {
        transitionTo(BookingStatus.CONFIRMED);
    }

    public void cancel(CancelReason reason) {
        transitionTo(BookingStatus.CANCELLED);
        cancelReason = reason;
    }

    private void transitionTo(BookingStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Booking %d cannot change status from %s to %s".formatted(id, status, target));
        }
        status = target;
    }
}
