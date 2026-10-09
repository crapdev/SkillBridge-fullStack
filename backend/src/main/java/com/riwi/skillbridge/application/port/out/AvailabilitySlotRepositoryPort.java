package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.AvailabilitySlot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilitySlotRepositoryPort {
    AvailabilitySlot save(AvailabilitySlot slot);
    Optional<AvailabilitySlot> findByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
    List<AvailabilitySlot> findAvailableByOfferingId(UUID offeringId);
    boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
    Optional<AvailabilitySlot> findById(UUID id);
    List<AvailabilitySlot> findByOfferingId(UUID offeringId);
    void deleteById(UUID id);
    void deleteByOfferingId(UUID offeringId);
}
