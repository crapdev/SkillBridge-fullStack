package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreatePaymentUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.ActivePaymentAlreadyExistsException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreatePaymentService implements CreatePaymentUseCase {

    private static final String CURRENCY = "COP";

    private final PaymentPort paymentPort;
    private final PaymentGatewayPort paymentGateway;
    private final BookingRepositoryPort bookingRepositoryPort;
    private final OfferingRepositoryPort offeringRepositoryPort;
    private final UserAccountPort userAccountPort;
    private final Clock clock;

    public CreatePaymentService(PaymentPort paymentPort,
                                PaymentGatewayPort paymentGateway,
                                BookingRepositoryPort bookingRepositoryPort,
                                OfferingRepositoryPort offeringRepositoryPort,
                                UserAccountPort userAccountPort,
                                Clock clock) {
        this.paymentPort = paymentPort;
        this.paymentGateway = paymentGateway;
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.offeringRepositoryPort = offeringRepositoryPort;
        this.userAccountPort = userAccountPort;
        this.clock = clock;
    }

    @Override
    public String createPaymentIntent(UUID bookingId, String customerEmail) {
        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        // 1. Buscamos la reserva real en la base de datos
        Booking booking = bookingRepositoryPort.findById(bookingId)
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada con ID: " + bookingId));

        // Nadie puede pagar (ni ver el monto de) una reserva ajena
        if (!booking.customerId().equals(customerId)) {
            throw new UnauthorizedActionException("No tienes permiso para pagar esta reserva");
        }
        if (!booking.isPayable()) {
            throw new BusinessRuleException("Esta reserva ya no está pendiente de pago");
        }

        // 2. Buscamos la mentoría (Offering) usando el ID que venía en la reserva
        Offering offering = offeringRepositoryPort.findById(booking.offeringId())
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));

        // 3. El precio sale del catálogo, nunca del cliente
        BigDecimal amount = offering.price();

        // 4. BUG-09: si la reserva ya tiene un pago en curso, se reutiliza en lugar de crear otro
        Optional<Payment> active = paymentPort.findActiveByBookingId(bookingId);
        if (active.isPresent()) {
            Optional<String> reused = reuseOrRelease(active.get(), amount);
            if (reused.isPresent()) {
                return reused.get();
            }
        } else {
            // Un cobro rechazado sigue aceptando otra tarjeta en Stripe (por ejemplo, desde otra pestaña): se cancela
            // para que no queden dos cobros que se puedan pagar. Si el pago anterior vino de reuseOrRelease, su
            // cobro ya quedó cancelado ahí.
            paymentPort.findLatestFailedByBookingId(bookingId).ifPresent(this::cancelRejectedCharge);
        }

        // 5. Le pedimos a la pasarela que prepare el cobro
        PaymentGatewayPort.PaymentIntent intent = paymentGateway.createPaymentIntent(bookingId, amount, CURRENCY);

        // 6. Guardamos el registro "PENDING"; el webhook lo marcará como pagado o fallido
        try {
            paymentPort.save(new Payment(
                null,
                bookingId,
                amount,
                CURRENCY,
                intent.id(),
                PaymentStatus.PENDING,
                clock.instant()
            ));
        } catch (ActivePaymentAlreadyExistsException e) {
            // Otra petición de la misma reserva (doble clic, dos pestañas) guardó su pago primero:
            // se cancela el cobro que acabamos de crear y se usa el que ganó
            paymentGateway.cancelPaymentIntent(intent.id());
            return paymentPort.findActiveByBookingId(bookingId)
                .flatMap(winner -> reuseOrRelease(winner, amount))
                .orElseThrow(() -> new BusinessRuleException("No fue posible iniciar el pago; inténtalo de nuevo"));
        }

        return intent.clientSecret();
    }

    /**
     * Cancela en la pasarela el cobro de un intento rechazado. Si no se puede porque el cliente lo está pagando
     * (reintentó con otra tarjeta en el mismo formulario), no se abre otro cobro: el webhook confirmará ese pago.
     */
    private void cancelRejectedCharge(Payment failed) {
        if (!paymentGateway.cancelPaymentIntent(failed.stripePaymentIntentId())) {
            throw new BusinessRuleException("Ya recibimos un pago para esta reserva; en un momento se confirmará");
        }
    }

    /**
     * Decide qué hacer con el pago activo de la reserva.
     *
     * @return el secreto del cobro existente si se puede seguir usando; vacío si se dejó como CANCELED y hay que
     *     crear uno nuevo
     */
    private Optional<String> reuseOrRelease(Payment payment, BigDecimal amount) {
        if (payment.status() == PaymentStatus.PAID) {
            throw new BusinessRuleException("Esta reserva ya fue pagada");
        }

        PaymentGatewayPort.ExistingPaymentIntent existing =
            paymentGateway.retrievePaymentIntent(payment.stripePaymentIntentId());
        switch (existing.state()) {
            case SUCCEEDED, PROCESSING ->
                // Stripe ya recibió el pago y el webhook todavía no llega: no se debe cobrar otra vez
                throw new BusinessRuleException("Ya recibimos un pago para esta reserva; en un momento se confirmará");
            case AWAITING_PAYMENT -> {
                if (payment.amount().compareTo(amount) == 0) {
                    return Optional.of(existing.intent().clientSecret());
                }
                // El precio cambió desde que se abrió el pago: no se reutiliza un cobro por otro monto
                if (!paymentGateway.cancelPaymentIntent(payment.stripePaymentIntentId())) {
                    throw new BusinessRuleException("Ya recibimos un pago para esta reserva; en un momento se confirmará");
                }
            }
            case CANCELED -> {
                // Ya no admite pagos (lo canceló Stripe o se canceló al expirar): se libera para crear otro
            }
        }

        paymentPort.save(new Payment(payment.id(), payment.bookingId(), payment.amount(), payment.currency(),
            payment.stripePaymentIntentId(), PaymentStatus.CANCELED, payment.createdAt()));
        return Optional.empty();
    }
}
