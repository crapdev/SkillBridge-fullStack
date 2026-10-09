package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ProcessPaymentUseCase;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Service;

@Service
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private final PaymentPort paymentPort;

    public ProcessPaymentService(PaymentPort paymentPort) {
        this.paymentPort = paymentPort;
    }

    @Override
    public void confirmPaymentSuccess(String stripeIntentId) {
        Payment payment = paymentPort.findByStripeIntentId(stripeIntentId)
            .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + stripeIntentId));

        // Creamos un nuevo record con el estado actualizado (los records son inmutables)
        Payment updatedPayment = new Payment(
            payment.id(),
            payment.bookingId(),
            payment.amount(),
            payment.currency(),
            payment.stripePaymentIntentId(),
            PaymentStatus.PAID,
            payment.createdAt()
        );

        paymentPort.save(updatedPayment);

        // NOTA: Aquí a futuro se podría emitir un evento (ej. BookingPaidEvent)
        // a RabbitMQ para enviar un correo de confirmación al usuario.
    }

    @Override
    public void markPaymentFailed(String stripeIntentId) {
        Payment payment = paymentPort.findByStripeIntentId(stripeIntentId)
            .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + stripeIntentId));

        Payment updatedPayment = new Payment(
            payment.id(),
            payment.bookingId(),
            payment.amount(),
            payment.currency(),
            payment.stripePaymentIntentId(),
            PaymentStatus.FAILED,
            payment.createdAt()
        );

        paymentPort.save(updatedPayment);
    }
}
