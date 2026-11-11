package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.domain.exception.SlotAlreadyTakenException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {
    /**
     * BUG-07: estados que ya no ocupan el horario. Una reserva en estos estados no impide volver a reservar.
     * Si HU-37 agrega un estado como EXPIRED, va aquí.
     */
    private static final Set<BookingStatus> RELEASED_STATUSES = EnumSet.of(BookingStatus.CANCELLED);
    /** BUG-08: índice único parcial de V10 (una sola reserva activa por mentoría y hora). */
    static final String ACTIVE_SLOT_CONSTRAINT = "uq_bookings_active_slot";

    private final JpaBookingRepository repository;

    public BookingPersistenceAdapter(JpaBookingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Booking> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity = new BookingEntity(
            booking.id(), booking.offeringId(), booking.customerId(), booking.scheduledAt(), booking.status(), Instant.now(),
            booking.price());
        try {
            // BUG-08: flush inmediato para que el índice único falle aquí y no al confirmar la transacción
            BookingEntity saved = repository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            String detail = e.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains(ACTIVE_SLOT_CONSTRAINT)) {
                throw new SlotAlreadyTakenException();
            }
            throw e;
        }
    }

    @Override
    public List<Booking> findByCustomerId(UUID customerId) {
        return repository.findByCustomerIdOrderByScheduledAtDesc(customerId)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Booking> findByOfferingIds(Collection<UUID> offeringIds) {
        // IN () vacío no es SQL válido en todas las bases: sin mentorías no hay reservas que buscar
        if (offeringIds.isEmpty()) return List.of();
        return repository.findByOfferingIdIn(offeringIds)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public boolean existsDuplicateBooking(UUID customerId, UUID offeringId, Instant scheduledAt) {
        return repository.existsByCustomerIdAndOfferingIdAndScheduledAt(customerId, offeringId, scheduledAt);
    }

    @Override
    public boolean existsActiveByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt) {
        return repository.existsByOfferingIdAndScheduledAtAndStatusNotIn(offeringId, scheduledAt, RELEASED_STATUSES);
    }

    @Override
    public boolean existsActiveByCustomerIdAndScheduledAt(UUID customerId, Instant scheduledAt) {
        return repository.existsByCustomerIdAndScheduledAtAndStatusNotIn(customerId, scheduledAt, RELEASED_STATUSES);
    }

    @Override
    public List<Booking> findConfirmedScheduledBefore(Instant instant) {
        return repository.findByStatusAndScheduledAtBefore(BookingStatus.CONFIRMED, instant)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public boolean updateStatusIf(UUID id, BookingStatus expected, BookingStatus next) {
        return repository.updateStatusIf(id, expected, next) == 1;
    }

    private Booking toDomain(BookingEntity entity) {
        return new Booking(entity.getId(), entity.getOfferingId(), entity.getCustomerId(), entity.getScheduledAt(),
            entity.getStatus(), entity.getPrice());
    }
}
