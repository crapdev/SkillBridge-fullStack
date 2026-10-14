package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.application.port.in.ReviewOfferingsUseCase.OfferingForReview;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import com.riwi.skillbridge.domain.model.SyllabusTopic;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Mentoría tal como la revisa el administrador (HU-21): su ficha completa y quién la propone. */
public record AdminOfferingResponse(
        UUID id,
        String title,
        String description,
        String category,
        BigDecimal price,
        List<SyllabusTopic> syllabus,
        int durationMinutes,
        OfferingLevel level,
        Set<DayOfWeek> workingDays,
        OfferingReviewStatus reviewStatus,
        String rejectionReason,
        UUID providerId,
        String providerName,
        String providerEmail) {

    public static AdminOfferingResponse from(OfferingForReview review) {
        Offering o = review.offering();
        return new AdminOfferingResponse(o.id(), o.title(), o.description(), o.category(), o.price(), o.syllabus(),
                o.durationMinutes(), o.level(), o.workingDays(), o.reviewStatus(), o.rejectionReason(),
                o.providerId(), review.providerName(), review.providerEmail());
    }
}
