package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.PublicReview;
import com.riwi.skillbridge.domain.model.RatingSummary;
import com.riwi.skillbridge.domain.model.Review;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HU-27: calificaciones de reservas (tabla reviews, migración V20). */
public interface ReviewRepositoryPort {

    /**
     * Guarda la calificación. Lanza {@link com.riwi.skillbridge.domain.exception.ReviewAlreadyExistsException}
     * si la reserva ya tenía una (también cuando dos peticiones llegan a la vez: lo garantiza la restricción UNIQUE).
     */
    Review save(Review review);

    boolean existsByBookingId(UUID bookingId);

    /** Calificaciones que dejó un cliente (para saber qué reservas ya calificó). */
    List<Review> findByCustomerId(UUID customerId);

    /** Promedio y cantidad por mentoría. Las mentorías sin calificaciones no aparecen en el mapa. */
    Map<UUID, RatingSummary> summarizeByOfferingIds(Collection<UUID> offeringIds);

    /** Promedio y cantidad de las calificaciones al mentor, sumando todas sus mentorías. */
    RatingSummary summarizeMentor(UUID providerId);

    /** Las calificaciones más recientes de una mentoría que tienen algún comentario. */
    List<PublicReview> findLatestCommentedByOfferingId(UUID offeringId, int limit);
}
