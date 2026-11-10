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

    /**
     * BUG-09: consulta un cobro ya creado para decidir si se puede reutilizar (mismo secreto para el frontend)
     * en lugar de crear otro. Un cobro que la pasarela ya no conoce se informa como {@link IntentState#CANCELED}.
     */
    ExistingPaymentIntent retrievePaymentIntent(String paymentIntentId);

    /**
     * BUG-09: cancela en la pasarela un cobro que ya no se va a usar, para que nadie pueda pagarlo después.
     *
     * @return {@code true} si el cobro quedó cancelado (o ya lo estaba); {@code false} si no se puede cancelar
     *     porque ya se pagó o se está procesando
     */
    boolean cancelPaymentIntent(String paymentIntentId);

    record PaymentIntent(String id, String clientSecret) {}

    record ExistingPaymentIntent(PaymentIntent intent, IntentState state) {}

    enum PaymentOutcome { SUCCEEDED, FAILED, PENDING }

    /** En qué punto está un cobro, visto desde quien quiere reutilizarlo. */
    enum IntentState {
        /** Espera que el cliente pague: se puede reutilizar. */
        AWAITING_PAYMENT,
        /** El cliente ya pagó y la pasarela lo está procesando. */
        PROCESSING,
        SUCCEEDED,
        /** Ya no admite pagos. */
        CANCELED
    }
}
