package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.PublicReview;
import com.riwi.skillbridge.domain.model.RatingSummary;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** HU-27: lo que el público ve de las calificaciones, en el catálogo y en el detalle. */
public interface GetOfferingRatingsUseCase {

    /** Promedio de una mentoría para el catálogo (incluye las que aún no tienen calificaciones). */
    record OfferingRating(UUID offeringId, Double average, long count) {}

    /** Detalle: promedio de la mentoría, promedio del mentor y las calificaciones recientes con comentario. */
    record OfferingReviews(RatingSummary offering, RatingSummary mentor, List<PublicReview> recent) {}

    List<OfferingRating> ratingsOf(Collection<UUID> offeringIds);

    OfferingReviews reviewsOf(UUID offeringId);
}
