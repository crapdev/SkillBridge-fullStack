package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaOfferingRepository extends JpaRepository<OfferingEntity, UUID> {
    List<OfferingEntity> findByActiveTrueOrderByTitleAsc();
    List<OfferingEntity> findByProviderId(UUID providerId);

    @Query("""
        SELECT DISTINCT o FROM OfferingEntity o
        WHERE o.active = true
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
        ORDER BY o.title ASC
    """)
    List<OfferingEntity> findVisibleOfferings(@Param("now") Instant now);

    @Query(value = """
        SELECT DISTINCT o FROM OfferingEntity o
        WHERE o.active = true
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
    """, countQuery = """
        SELECT COUNT(DISTINCT o) FROM OfferingEntity o
        WHERE o.active = true
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
    """)
    Page<OfferingEntity> findVisibleOfferings(@Param("now") Instant now, Pageable pageable);

    @Query("""
        SELECT o FROM OfferingEntity o
        WHERE o.id = :id
          AND o.active = true
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
    """)
    Optional<OfferingEntity> findVisibleOfferingById(@Param("id") UUID id, @Param("now") Instant now);
}
