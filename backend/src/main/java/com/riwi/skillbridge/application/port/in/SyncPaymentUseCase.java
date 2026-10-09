package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.PaymentStatus;

/**
 * Actualiza un pago propio consultando su estado real en la pasarela. Complementa al webhook:
 * si el webhook no llega (por ejemplo, en local sin Stripe CLI), el pago igual se refleja.
 */
public interface SyncPaymentUseCase {
    PaymentStatus syncPayment(String paymentIntentId, String customerEmail);
}
