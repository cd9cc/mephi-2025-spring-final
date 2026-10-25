package ru.mephi.hotelbooking.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_rooms_hotel_number", columnNames = {"hotel_id", "number"}),
        indexes = @Index(name = "idx_rooms_times_booked", columnList = "times_booked, id"))
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rooms_hotel"))
    private Hotel hotel;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "times_booked", nullable = false)
    private long timesBooked;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
