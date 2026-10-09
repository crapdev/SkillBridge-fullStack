package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.AvailabilitySlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaAvailabilitySlotRepository extends JpaRepository<AvailabilitySlotEntity, UUID> {
    Optional<AvailabilitySlotEntity> findByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
    List<AvailabilitySlotEntity> findByOfferingIdAndReservedFalseOrderByScheduledAtAsc(UUID offeringId);
    boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
}
