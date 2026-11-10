package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPort {
    /**
     * Guarda un pago nuevo o actualiza uno existente.
     *
     * @throws com.riwi.skillbridge.domain.exception.ActivePaymentAlreadyExistsException si la reserva ya tiene
     *     otro pago PENDING o PAID (BUG-09)
     */
    Payment save(Payment payment);

    // Busca un pago utilizando el ID que nos devuelva el Webhook de Stripe
    Optional<Payment> findByStripeIntentId(String intentId);

    /** BUG-09: el pago activo (PENDING o PAID) de la reserva. La base de datos garantiza que hay como máximo uno. */
    Optional<Payment> findActiveByBookingId(UUID bookingId);
}
