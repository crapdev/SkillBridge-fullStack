package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    List<Booking> findByCustomerId(UUID customerId);
    boolean existsDuplicateBooking(UUID customerId, UUID offeringId, Instant scheduledAt);

    // NUEVO MÉTODO AGREGADO:
    Optional<Booking> findById(UUID id);
}
