package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepositoryPort {
    Booking save(Booking booking);
    List<Booking> findByCustomerId(UUID customerId);
    boolean existsDuplicateBooking(UUID customerId, UUID offeringId, Instant scheduledAt);
    boolean existsByCustomerIdAndScheduledAt(UUID customerId, Instant scheduledAt);
    boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
}
