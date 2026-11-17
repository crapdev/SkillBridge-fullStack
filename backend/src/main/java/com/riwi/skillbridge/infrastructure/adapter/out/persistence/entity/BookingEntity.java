package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.BookingStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class BookingEntity {
    @Id
    private UUID id;
    @Column(name = "offering_id")
    private UUID offeringId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    @Enumerated(EnumType.STRING)
    private BookingStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    /** BUG-10: precio con el que se hizo la reserva. No se actualiza: cancelar o confirmar no lo cambia. */
    @Column(name = "price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal price;

    protected BookingEntity() {}

    public BookingEntity(UUID id, UUID offeringId, UUID customerId, Instant scheduledAt, BookingStatus status, Instant createdAt,
                         BigDecimal price) {
        this.id = id;
        this.offeringId = offeringId;
        this.customerId = customerId;
        this.scheduledAt = scheduledAt;
        this.status = status;
        this.createdAt = createdAt;
        this.price = price;
    }

    public UUID getId() { return id; }
    public UUID getOfferingId() { return offeringId; }
    public UUID getCustomerId() { return customerId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getPrice() { return price; }
}
