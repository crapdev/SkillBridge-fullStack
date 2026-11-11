package com.riwi.skillbridge.domain.exception;

/** HU-27: la reserva ya tiene una calificación; cada reserva se califica una sola vez. */
public class ReviewAlreadyExistsException extends RuntimeException {
    public ReviewAlreadyExistsException() {
        super("Ya calificaste esta mentoría");
    }
}
