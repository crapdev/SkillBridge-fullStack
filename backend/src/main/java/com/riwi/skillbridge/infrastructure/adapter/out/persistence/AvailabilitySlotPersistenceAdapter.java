package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.AvailabilitySlotEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaAvailabilitySlotRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AvailabilitySlotPersistenceAdapter implements AvailabilitySlotRepositoryPort {

    private final JpaAvailabilitySlotRepository repository;

    public AvailabilitySlotPersistenceAdapter(JpaAvailabilitySlotRepository repository) {
        this.repository = repository;
    }

    @Override
    public AvailabilitySlot save(AvailabilitySlot slot) {
        AvailabilitySlotEntity entity = new AvailabilitySlotEntity(
            slot.id(), slot.offeringId(), slot.scheduledAt(), slot.reserved()
        );
        AvailabilitySlotEntity saved = repository.save(entity);
        return new AvailabilitySlot(saved.getId(), saved.getOfferingId(), saved.getScheduledAt(), saved.isReserved());
    }

    @Override
    public Optional<AvailabilitySlot> findByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt) {
        return repository.findByOfferingIdAndScheduledAt(offeringId, scheduledAt)
            .map(entity -> new AvailabilitySlot(
                entity.getId(), entity.getOfferingId(), entity.getScheduledAt(), entity.isReserved()
            ));
    }

    @Override
    public List<AvailabilitySlot> findAvailableByOfferingId(UUID offeringId) {
        return repository.findByOfferingIdAndReservedFalseOrderByScheduledAtAsc(offeringId)
            .stream()
            .map(entity -> new AvailabilitySlot(
                entity.getId(), entity.getOfferingId(), entity.getScheduledAt(), entity.isReserved()
            ))
            .toList();
    }

    @Override
    public boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt) {
        return repository.existsByOfferingIdAndScheduledAt(offeringId, scheduledAt);
    }

    @Override
    public Optional<AvailabilitySlot> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<AvailabilitySlot> findByOfferingId(UUID offeringId) {
        return repository.findByOfferingIdOrderByScheduledAtAsc(offeringId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteByOfferingId(UUID offeringId) {
        repository.deleteByOfferingId(offeringId);
    }

    private AvailabilitySlot toDomain(AvailabilitySlotEntity e) {
        return new AvailabilitySlot(e.getId(), e.getOfferingId(), e.getScheduledAt(), e.isReserved());
    }
}
