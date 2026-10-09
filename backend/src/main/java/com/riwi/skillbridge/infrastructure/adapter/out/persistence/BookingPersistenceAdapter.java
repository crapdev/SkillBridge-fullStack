package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {
    private final JpaBookingRepository repository;

    public BookingPersistenceAdapter(JpaBookingRepository repository) {
        this.repository = repository;
    }

    @Override
    public java.util.Optional<Booking> findById(UUID id) {
        return repository.findById(id).map(entity -> new Booking(
            entity.getId(),
            entity.getOfferingId(),
            entity.getCustomerId(),
            entity.getScheduledAt(),
            entity.getStatus()
        ));
    }

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity = new BookingEntity(
            booking.id(), booking.offeringId(), booking.customerId(), booking.scheduledAt(), booking.status(), Instant.now());
        BookingEntity saved = repository.save(entity);
        return new Booking(saved.getId(), saved.getOfferingId(), saved.getCustomerId(), saved.getScheduledAt(), saved.getStatus());
    }

    @Override
    public List<Booking> findByCustomerId(UUID customerId) {
        return repository.findByCustomerIdOrderByScheduledAtDesc(customerId)
            .stream()
            .map(entity -> new Booking(
                entity.getId(),
                entity.getOfferingId(),
                entity.getCustomerId(),
                entity.getScheduledAt(),
                entity.getStatus()
            ))
            .toList();
    }

    @Override
    public boolean existsDuplicateBooking(UUID customerId, UUID offeringId, Instant scheduledAt) {
        return repository.existsByCustomerIdAndOfferingIdAndScheduledAt(customerId, offeringId, scheduledAt);
    }

    @Override
    public boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt) {
        return repository.existsByOfferingIdAndScheduledAt(offeringId, scheduledAt);
    }

    @Override
    public boolean existsByCustomerIdAndScheduledAt(UUID customerId, Instant scheduledAt) {
        return repository.existsByCustomerIdAndScheduledAt(customerId, scheduledAt);
    }
}
