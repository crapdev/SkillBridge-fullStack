package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Payment;
import java.util.Optional;

public interface PaymentPort {
    // Guarda un pago nuevo o actualiza uno existente
    Payment save(Payment payment);

    // Busca un pago utilizando el ID que nos devuelva el Webhook de Stripe
    Optional<Payment> findByStripeIntentId(String intentId);
}
