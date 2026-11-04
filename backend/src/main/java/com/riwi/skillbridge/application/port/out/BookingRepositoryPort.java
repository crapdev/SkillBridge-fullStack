package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepositoryPort {
    Optional<Booking> findById(UUID id);
    Booking save(Booking booking);
    List<Booking> findByCustomerId(UUID customerId);
    /** HU-22: reservas que recibe un proveedor (las de sus mentorías). */
    List<Booking> findByOfferingIds(Collection<UUID> offeringIds);
    boolean existsDuplicateBooking(UUID customerId, UUID offeringId, Instant scheduledAt);
    boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
    boolean existsByCustomerIdAndScheduledAt(UUID customerId, Instant scheduledAt);
    /** BUG-05: reservas confirmadas cuya sesión empezó antes de ese instante (candidatas a completarse). */
    List<Booking> findConfirmedScheduledBefore(Instant instant);
    /**
     * BUG-05: cambia el estado solo si la reserva sigue en {@code expected}. Devuelve false si otro proceso
     * ya la cambió, para no repetir lo que sigue (por ejemplo, publicar el evento).
     */
    boolean updateStatusIf(UUID id, BookingStatus expected, BookingStatus next);
}
