package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.application.port.in.RateBookingUseCase.ReviewDraft;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HU-27: estrellas de 1 a 5 y comentario opcional, para la mentoría y para el mentor. */
public record RateBookingRequest(
        @NotNull(message = "califica la mentoría") @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment,
        @NotNull(message = "califica al mentor") @Min(1) @Max(5) Integer mentorRating,
        @Size(max = 500) String mentorComment) {

    public ReviewDraft toDraft() {
        return new ReviewDraft(rating, comment, mentorRating, mentorComment);
    }
}
