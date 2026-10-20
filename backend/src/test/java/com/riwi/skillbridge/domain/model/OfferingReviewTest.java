package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** SCRUM-178 (HU-21): reglas de la revisión de mentorías en el dominio. */
class OfferingReviewTest {

    private static Offering approved() {
        return new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
    }

    private static Offering pending() {
        return approved().pendingReview();
    }

    @Test
    @DisplayName("Sin estado (mentorías previas o datos de caché viejos) cuenta como aprobada")
    void withoutStatus_isApproved() {
        Offering legacy = approved();

        assertEquals(OfferingReviewStatus.APPROVED, legacy.reviewStatus());
        assertTrue(legacy.isApproved());
    }

    @Test
    void approve_movesPendingToApproved() {
        Offering result = pending().approve();

        assertEquals(OfferingReviewStatus.APPROVED, result.reviewStatus());
        assertNull(result.rejectionReason());
    }

    @Test
    void reject_keepsTheTrimmedReason() {
        Offering result = pending().reject("  El temario no corresponde a la categoría  ");

        assertEquals(OfferingReviewStatus.REJECTED, result.reviewStatus());
        assertEquals("El temario no corresponde a la categoría", result.rejectionReason());
        assertFalse(result.isApproved());
    }

    @Test
    void reject_reasonIsOptional() {
        assertNull(pending().reject("   ").rejectionReason());
        assertNull(pending().reject(null).rejectionReason());
    }

    @Test
    void reject_tooLongReasonFails() {
        String longReason = "x".repeat(Offering.MAX_REJECTION_REASON + 1);

        assertThrows(BusinessRuleException.class, () -> pending().reject(longReason));
    }

    @Test
    @DisplayName("Solo se revisa lo que está pendiente")
    void onlyPendingCanBeReviewed() {
        assertThrows(BusinessRuleException.class, () -> approved().approve());
        assertThrows(BusinessRuleException.class, () -> approved().reject("motivo"));
        Offering rejected = pending().reject("motivo");
        assertThrows(BusinessRuleException.class, rejected::approve);
    }

    @Test
    @DisplayName("Corregir una rechazada la reenvía a revisión sin el motivo anterior")
    void resubmitted_rejectedGoesBackToReview() {
        Offering result = pending().reject("Falta el temario").resubmitted();

        assertEquals(OfferingReviewStatus.PENDING_REVIEW, result.reviewStatus());
        assertNull(result.rejectionReason());
    }

    @Test
    @DisplayName("Corregir una aprobada o pendiente no cambia su estado")
    void resubmitted_keepsApprovedAndPending() {
        assertEquals(OfferingReviewStatus.APPROVED, approved().resubmitted().reviewStatus());
        assertEquals(OfferingReviewStatus.PENDING_REVIEW, pending().resubmitted().reviewStatus());
    }

    @Test
    @DisplayName("El motivo solo existe en una mentoría rechazada")
    void reasonOnlyOnRejected() {
        Offering approvedWithReason = new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java", "Mentoría", "BACKEND",
                BigDecimal.TEN, true, null, 60, OfferingLevel.BASICO, null, OfferingReviewStatus.APPROVED, "sobrante");

        assertNull(approvedWithReason.rejectionReason());
    }
}
