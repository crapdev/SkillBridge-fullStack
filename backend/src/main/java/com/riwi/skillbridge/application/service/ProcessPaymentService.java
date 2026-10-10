package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ProcessPaymentUseCase;
import com.riwi.skillbridge.application.port.in.SyncPaymentUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort.PaymentOutcome;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProcessPaymentService implements ProcessPaymentUseCase, SyncPaymentUseCase {

    private final PaymentPort paymentPort;
    private final BookingRepositoryPort bookingRepositoryPort;
    private final PaymentGatewayPort paymentGateway;
    private final UserAccountPort userAccountPort;

    public ProcessPaymentService(PaymentPort paymentPort, BookingRepositoryPort bookingRepositoryPort,
                                 PaymentGatewayPort paymentGateway, UserAccountPort userAccountPort) {
        this.paymentPort = paymentPort;
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.paymentGateway = paymentGateway;
        this.userAccountPort = userAccountPort;
    }

    @Override
    public PaymentStatus syncPayment(String paymentIntentId, String customerEmail) {
        Payment payment = paymentPort.findByStripeIntentId(paymentIntentId)
            .orElseThrow(() -> new DomainNotFoundException("Pago no encontrado"));

        // Solo el dueño de la reserva puede consultar y actualizar su pago
        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        Booking booking = bookingRepositoryPort.findById(payment.bookingId())
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));
        if (!booking.customerId().equals(customerId)) {
            throw new UnauthorizedActionException("No tienes permiso para consultar este pago");
        }

        // Ya procesado (por el webhook o una consulta anterior): no hace falta preguntar a la pasarela
        if (payment.status() != PaymentStatus.PENDING) {
            return payment.status();
        }

        PaymentOutcome outcome = paymentGateway.checkOutcome(paymentIntentId);
        if (outcome == PaymentOutcome.SUCCEEDED) {
            confirmPaymentSuccess(paymentIntentId);
            return PaymentStatus.PAID;
        }
        if (outcome == PaymentOutcome.FAILED) {
            markPaymentFailed(paymentIntentId);
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
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

        // El pago aprobado confirma la reserva. Si mientras tanto se canceló, se deja como está:
        // lanzar un error aquí haría que Stripe reintente el webhook indefinidamente.
        bookingRepositoryPort.findById(payment.bookingId())
            .filter(Booking::isPayable)
            .ifPresent(booking -> bookingRepositoryPort.save(booking.confirm()));

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
