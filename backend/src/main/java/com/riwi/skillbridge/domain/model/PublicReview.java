package com.riwi.skillbridge.domain.model;

import java.time.Instant;

/**
 * HU-27: calificación tal como se muestra en el detalle de una mentoría. Del cliente solo va su primer nombre:
 * nunca el correo ni el nombre completo.
 */
public record PublicReview(
        int rating,
        String comment,
        int mentorRating,
        String mentorComment,
        String authorName,
        Instant createdAt) {

    /** Primer nombre de quien calificó ("Laura Gómez" -> "Laura"); sin nombre se muestra "Cliente". */
    public static String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Cliente";
        return fullName.trim().split("\\s+")[0];
    }
}
