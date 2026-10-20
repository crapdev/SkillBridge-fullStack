package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
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
    // HU-21: cola de revisión del administrador, la que más lleva esperando primero
    List<OfferingEntity> findByReviewStatusOrderByCreatedAtAsc(OfferingReviewStatus reviewStatus);

    // Mentoría visible (HU-17, HU-21): activa, aprobada por el admin, con al menos un horario futuro libre y de un proveedor con cuenta ACTIVE
    // (aprobado; no pendiente, rechazado ni desactivado por el admin). Las tres consultas usan el mismo criterio.
    @Query("""
        SELECT DISTINCT o FROM OfferingEntity o
        WHERE o.active = true
          AND o.reviewStatus = com.riwi.skillbridge.domain.model.OfferingReviewStatus.APPROVED
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
          AND EXISTS (
            SELECT 1 FROM UserEntity u
            WHERE u.id = o.providerId
              AND u.status = com.riwi.skillbridge.domain.model.AccountStatus.ACTIVE
          )
        ORDER BY o.title ASC
    """)
    List<OfferingEntity> findVisibleOfferings(@Param("now") Instant now);

    @Query(value = """
        SELECT DISTINCT o FROM OfferingEntity o
        WHERE o.active = true
          AND o.reviewStatus = com.riwi.skillbridge.domain.model.OfferingReviewStatus.APPROVED
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
          AND EXISTS (
            SELECT 1 FROM UserEntity u
            WHERE u.id = o.providerId
              AND u.status = com.riwi.skillbridge.domain.model.AccountStatus.ACTIVE
          )
    """, countQuery = """
        SELECT COUNT(DISTINCT o) FROM OfferingEntity o
        WHERE o.active = true
          AND o.reviewStatus = com.riwi.skillbridge.domain.model.OfferingReviewStatus.APPROVED
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
          AND EXISTS (
            SELECT 1 FROM UserEntity u
            WHERE u.id = o.providerId
              AND u.status = com.riwi.skillbridge.domain.model.AccountStatus.ACTIVE
          )
    """)
    Page<OfferingEntity> findVisibleOfferings(@Param("now") Instant now, Pageable pageable);

    @Query("""
        SELECT o FROM OfferingEntity o
        WHERE o.id = :id
          AND o.active = true
          AND o.reviewStatus = com.riwi.skillbridge.domain.model.OfferingReviewStatus.APPROVED
          AND EXISTS (
            SELECT 1 FROM AvailabilitySlotEntity s
            WHERE s.offeringId = o.id
              AND s.reserved = false
              AND s.scheduledAt > :now
          )
          AND EXISTS (
            SELECT 1 FROM UserEntity u
            WHERE u.id = o.providerId
              AND u.status = com.riwi.skillbridge.domain.model.AccountStatus.ACTIVE
          )
    """)
    Optional<OfferingEntity> findVisibleOfferingById(@Param("id") UUID id, @Param("now") Instant now);
}
