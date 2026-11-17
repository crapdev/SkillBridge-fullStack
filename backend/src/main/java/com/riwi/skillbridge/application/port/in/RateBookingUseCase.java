package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Review;

import java.util.List;
import java.util.UUID;

/** HU-27: el cliente califica una reserva propia y completada, una sola vez. */
public interface RateBookingUseCase {

    /** Lo que el cliente envía: estrellas (1 a 5) y comentario opcional, para la mentoría y para el mentor. */
    record ReviewDraft(int rating, String comment, int mentorRating, String mentorComment) {}

    Review rate(UUID bookingId, String customerEmail, ReviewDraft draft);

    /** Calificaciones del cliente de la sesión: "Mis reservas" las usa para no ofrecer calificar dos veces. */
    List<Review> myReviews(String customerEmail);
}
