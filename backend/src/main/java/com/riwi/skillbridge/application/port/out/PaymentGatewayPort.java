package com.riwi.skillbridge.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pasarela de pagos externa (hoy Stripe). Los casos de uso dependen de este puerto
 * y no del SDK del proveedor, así se puede cambiar o simular en tests.
 */
public interface PaymentGatewayPort {

    /**
     * Prepara un cobro en la pasarela.
     *
     * @return el ID de la intención de pago y el secreto que el frontend usa para completarlo
     */
    PaymentIntent createPaymentIntent(UUID bookingId, BigDecimal amount, String currency);

    /** Consulta en la pasarela el estado real de un cobro (no se confía en lo que diga el navegador). */
    PaymentOutcome checkOutcome(String paymentIntentId);

    record PaymentIntent(String id, String clientSecret) {}

    enum PaymentOutcome { SUCCEEDED, FAILED, PENDING }
}
