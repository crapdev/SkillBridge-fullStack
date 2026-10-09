package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, UUID> {
    List<BookingEntity> findByCustomerIdOrderByScheduledAtDesc(UUID customerId);
    boolean existsByCustomerIdAndOfferingIdAndScheduledAt(UUID customerId, UUID offeringId, Instant scheduledAt);
    boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
    boolean existsByCustomerIdAndScheduledAt(UUID customerId, Instant scheduledAt);
}
