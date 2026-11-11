package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-27: calificación que un cliente deja sobre una reserva completada. Califica dos cosas por separado:
 * la mentoría (la sesión) y el mentor (el proveedor que la dio). Los comentarios son opcionales.
 */
public record Review(
        UUID id,
        UUID bookingId,
        int rating,
        String comment,
        int mentorRating,
        String mentorComment,
        Instant createdAt) {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    /** Largo máximo de cada comentario (las columnas de la migración V20). */
    public static final int MAX_COMMENT = 500;

    public Review {
        requireValidRating(rating, "la mentoría");
        requireValidRating(mentorRating, "el mentor");
        comment = cleanComment(comment);
        mentorComment = cleanComment(mentorComment);
    }

    /** Calificación nueva de una reserva: el id y la fecha los asigna el dominio. */
    public static Review create(UUID bookingId, int rating, String comment, int mentorRating, String mentorComment,
                                Instant now) {
        return new Review(UUID.randomUUID(), bookingId, rating, comment, mentorRating, mentorComment, now);
    }

    private static void requireValidRating(int value, String target) {
        if (value < MIN_RATING || value > MAX_RATING) {
            throw new BusinessRuleException("La calificación de " + target + " debe ser de "
                    + MIN_RATING + " a " + MAX_RATING + " estrellas");
        }
    }

    // Un comentario vacío o solo con espacios es "sin comentario"
    private static String cleanComment(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        if (trimmed.length() > MAX_COMMENT) {
            throw new BusinessRuleException("Cada comentario admite como máximo " + MAX_COMMENT + " caracteres");
        }
        return trimmed;
    }
}
