package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ProcessPaymentUseCase;
import com.riwi.skillbridge.application.port.in.SyncPaymentUseCase;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessPaymentService implements ProcessPaymentUseCase, SyncPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessPaymentService.class);

    private final PaymentPort paymentPort;
    private final BookingRepositoryPort bookingRepositoryPort;
    private final PaymentGatewayPort paymentGateway;
    private final UserAccountPort userAccountPort;
    private final BookingEventPublisherPort eventPublisher;

    public ProcessPaymentService(PaymentPort paymentPort, BookingRepositoryPort bookingRepositoryPort,
                                 PaymentGatewayPort paymentGateway, UserAccountPort userAccountPort,
                                 BookingEventPublisherPort eventPublisher) {
        this.paymentPort = paymentPort;
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.paymentGateway = paymentGateway;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
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

        // BUG-09: solo puede haber un pago activo por reserva. Si se pagó un intento viejo (otra pestaña con el
        // formulario abierto) mientras hay otro pendiente, se cancela el pendiente para registrar el cobro real.
        Optional<Payment> otherActive = paymentPort.findActiveByBookingId(payment.bookingId())
            .filter(other -> !other.id().equals(payment.id()));
        if (otherActive.isPresent()) {
            Payment other = otherActive.get();
            if (other.status() == PaymentStatus.PAID || !paymentGateway.cancelPaymentIntent(other.stripePaymentIntentId())) {
                // Se cobró dos veces la misma reserva. No se lanza error: Stripe reintentaría el webhook sin fin
                log.error("BUG-09: la reserva {} tiene dos cobros ({} ya registrado y {}). Requiere reembolso manual",
                    payment.bookingId(), other.stripePaymentIntentId(), stripeIntentId);
                return;
            }
            paymentPort.save(withStatus(other, PaymentStatus.CANCELED));
        }

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
        // Solo se avisa cuando la reserva cambia de verdad: un webhook repetido encuentra la reserva
        // ya confirmada y no vuelve a publicar el evento.
        bookingRepositoryPort.findById(payment.bookingId())
            .filter(Booking::isPayable)
            .ifPresent(booking -> {
                Booking confirmed = booking.confirm();
                bookingRepositoryPort.save(confirmed);
                eventPublisher.bookingPaid(confirmed);
            });
    }

    @Override
    public void markPaymentFailed(String stripeIntentId) {
        Payment payment = paymentPort.findByStripeIntentId(stripeIntentId)
            .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + stripeIntentId));

        // BUG-09: Stripe puede entregar los webhooks fuera de orden. El rechazo de un cobro que ya cancelamos
        // (porque se reemplazó por otro) no cambia nada ni avisa al cliente de un fallo que ya no importa.
        if (payment.status() == PaymentStatus.CANCELED) {
            return;
        }

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

        // La reserva sigue pendiente para que el cliente reintente; solo se avisa la primera vez que falla
        if (payment.status() != PaymentStatus.FAILED) {
            bookingRepositoryPort.findById(payment.bookingId()).ifPresent(eventPublisher::paymentFailed);
        }
    }

    private static Payment withStatus(Payment payment, PaymentStatus status) {
        return new Payment(payment.id(), payment.bookingId(), payment.amount(), payment.currency(),
            payment.stripePaymentIntentId(), status, payment.createdAt());
    }
}
