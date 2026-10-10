package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreatePaymentUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class CreatePaymentService implements CreatePaymentUseCase {

    private static final String CURRENCY = "COP";

    private final PaymentPort paymentPort;
    private final PaymentGatewayPort paymentGateway;
    private final BookingRepositoryPort bookingRepositoryPort;
    private final OfferingRepositoryPort offeringRepositoryPort;
    private final UserAccountPort userAccountPort;

    public CreatePaymentService(PaymentPort paymentPort,
                                PaymentGatewayPort paymentGateway,
                                BookingRepositoryPort bookingRepositoryPort,
                                OfferingRepositoryPort offeringRepositoryPort,
                                UserAccountPort userAccountPort) {
        this.paymentPort = paymentPort;
        this.paymentGateway = paymentGateway;
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.offeringRepositoryPort = offeringRepositoryPort;
        this.userAccountPort = userAccountPort;
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

        // 4. Le pedimos a la pasarela que prepare el cobro
        PaymentGatewayPort.PaymentIntent intent = paymentGateway.createPaymentIntent(bookingId, amount, CURRENCY);

        // 5. Guardamos el registro "PENDING"; el webhook lo marcará como pagado o fallido
        paymentPort.save(new Payment(
            null,
            bookingId,
            amount,
            CURRENCY,
            intent.id(),
            PaymentStatus.PENDING,
            Instant.now()
        ));

        return intent.clientSecret();
    }
}
