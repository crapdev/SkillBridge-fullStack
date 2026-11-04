package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaBookingRepository extends JpaRepository<BookingEntity, UUID> {
    List<BookingEntity> findByCustomerIdOrderByScheduledAtDesc(UUID customerId);
    List<BookingEntity> findByOfferingIdIn(Collection<UUID> offeringIds);
    boolean existsByCustomerIdAndOfferingIdAndScheduledAt(UUID customerId, UUID offeringId, Instant scheduledAt);
    boolean existsByOfferingIdAndScheduledAt(UUID offeringId, Instant scheduledAt);
    boolean existsByCustomerIdAndScheduledAt(UUID customerId, Instant scheduledAt);
    List<BookingEntity> findByStatusAndScheduledAtBefore(BookingStatus status, Instant instant);

    // BUG-05: el WHERE con el estado esperado hace el cambio atómico; 0 filas = otro proceso ya lo hizo
    @Modifying(clearAutomatically = true)
    @Query("UPDATE BookingEntity b SET b.status = :next WHERE b.id = :id AND b.status = :expected")
    int updateStatusIf(@Param("id") UUID id, @Param("expected") BookingStatus expected, @Param("next") BookingStatus next);
}
