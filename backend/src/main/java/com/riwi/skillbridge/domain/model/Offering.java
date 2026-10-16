package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mentoría publicada por un proveedor.
 * Desde HU-20 incluye su ficha: temario, duración de cada sesión, nivel y días de la semana en que se ofrece.
 * Desde HU-21 pasa por la revisión de un administrador antes de poder aparecer en el catálogo.
 */
public record Offering(
        UUID id,
        UUID providerId,
        String title,
        String description,
        String category,
        BigDecimal price,
        boolean active,
        List<SyllabusTopic> syllabus,
        int durationMinutes,
        OfferingLevel level,
        Set<DayOfWeek> workingDays,
        OfferingReviewStatus reviewStatus,
        String rejectionReason
) {
    /** Duración por defecto de una sesión cuando no se indica (la misma de la migración V8). */
    public static final int DEFAULT_DURATION_MINUTES = 60;
    /** Largo máximo del motivo de rechazo (la columna de la migración V9). */
    public static final int MAX_REJECTION_REASON = 300;

    public Offering {
        // Valores por defecto válidos: protegen datos viejos (p. ej. la caché) que aún no traen la ficha
        syllabus = syllabus == null ? List.of() : List.copyOf(syllabus);
        durationMinutes = durationMinutes > 0 ? durationMinutes : DEFAULT_DURATION_MINUTES;
        level = level == null ? OfferingLevel.BASICO : level;
        workingDays = orderedDays(workingDays);
        // Sin dato = aprobada, como las mentorías que existían antes de la revisión (migración V9)
        reviewStatus = reviewStatus == null ? OfferingReviewStatus.APPROVED : reviewStatus;
        // El motivo solo tiene sentido en una mentoría rechazada
        rejectionReason = reviewStatus == OfferingReviewStatus.REJECTED ? blankToNull(rejectionReason) : null;
    }

    /** Mentoría con ficha y ya aprobada: la forma que tenían todas antes de HU-21. */
    public Offering(UUID id, UUID providerId, String title, String description, String category,
                    BigDecimal price, boolean active, List<SyllabusTopic> syllabus, int durationMinutes,
                    OfferingLevel level, Set<DayOfWeek> workingDays) {
        this(id, providerId, title, description, category, price, active, syllabus, durationMinutes, level,
                workingDays, OfferingReviewStatus.APPROVED, null);
    }

    /**
     * Mentoría sin ficha: temario vacío, 60 minutos, nivel básico, todos los días y aprobada.
     * Se conserva para el código y las pruebas que solo necesitan los datos básicos.
     */
    public Offering(UUID id, UUID providerId, String title, String description, String category,
                    BigDecimal price, boolean active) {
        this(id, providerId, title, description, category, price, active,
                List.of(), DEFAULT_DURATION_MINUTES, OfferingLevel.BASICO, EnumSet.allOf(DayOfWeek.class));
    }

    /** ¿El proveedor ofrece esta mentoría ese día de la semana? */
    public boolean offersOn(DayOfWeek day) {
        return workingDays.contains(day);
    }

    public boolean isApproved() {
        return reviewStatus == OfferingReviewStatus.APPROVED;
    }

    /** Toda mentoría nueva espera la revisión de un administrador antes de ser visible. */
    public Offering pendingReview() {
        return withReview(OfferingReviewStatus.PENDING_REVIEW, null);
    }

    public Offering approve() {
        requirePending();
        return withReview(OfferingReviewStatus.APPROVED, null);
    }

    /** El motivo es opcional; si se da, el proveedor lo ve en su panel para poder corregir la mentoría. */
    public Offering reject(String reason) {
        requirePending();
        if (reason != null && reason.trim().length() > MAX_REJECTION_REASON) {
            throw new BusinessRuleException("El motivo del rechazo admite como máximo " + MAX_REJECTION_REASON + " caracteres");
        }
        return withReview(OfferingReviewStatus.REJECTED, reason);
    }

    /**
     * Editar una mentoría rechazada es la forma de corregirla y reenviarla: vuelve a revisión sin el motivo anterior.
     * Una aprobada o pendiente conserva su estado (una corrección menor no la saca del catálogo).
     */
    public Offering resubmitted() {
        return reviewStatus == OfferingReviewStatus.REJECTED ? pendingReview() : this;
    }

    private void requirePending() {
        if (reviewStatus != OfferingReviewStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("Solo se pueden revisar mentorías que están pendientes de revisión");
        }
    }

    private Offering withReview(OfferingReviewStatus status, String reason) {
        return new Offering(id, providerId, title, description, category, price, active, syllabus,
                durationMinutes, level, workingDays, status, reason);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // Lunes a domingo en orden, sin duplicados e inmodificable. Sin dato (null) = todos los días, como en la migración
    private static Set<DayOfWeek> orderedDays(Collection<DayOfWeek> days) {
        if (days == null) return Collections.unmodifiableSet(EnumSet.allOf(DayOfWeek.class));
        if (days.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(days));
    }
}
