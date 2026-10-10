package com.riwi.skillbridge.application.port.in;

import java.util.UUID;

public interface CreatePaymentUseCase {
    /** Inicia el cobro de una reserva propia y devuelve el secreto que usa el formulario de pago. */
    String createPaymentIntent(UUID bookingId, String customerEmail);
}
