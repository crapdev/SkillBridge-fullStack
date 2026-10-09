package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreatePaymentUseCase;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class CreatePaymentService implements CreatePaymentUseCase {

    private final PaymentPort paymentPort;
    private final BookingRepositoryPort bookingRepositoryPort;
    private final OfferingRepositoryPort offeringRepositoryPort;

    @Value("${stripe.api.key.secret}")
    private String stripeSecretKey;

    public CreatePaymentService(PaymentPort paymentPort,
                                BookingRepositoryPort bookingRepositoryPort,
                                OfferingRepositoryPort offeringRepositoryPort) {
        this.paymentPort = paymentPort;
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.offeringRepositoryPort = offeringRepositoryPort;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Override
    public String createPaymentIntent(UUID bookingId) {
        try {
            // 1. Buscamos la reserva real en la base de datos
            Booking booking = bookingRepositoryPort.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + bookingId));

            // 2. Buscamos la mentoría (Offering) usando el ID que venía en la reserva
            Offering offering = offeringRepositoryPort.findById(booking.offeringId())
                .orElseThrow(() -> new IllegalArgumentException("Mentoría no encontrada"));

            // 3. Extraemos el precio real
            BigDecimal amountCop = offering.price();

            // Convertimos a Long porque Stripe lo requiere así (en COP no maneja decimales)
            long amountForStripe = amountCop.longValue();

            // Le decimos a Stripe que prepare el cobro con el valor exacto
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountForStripe)
                .setCurrency("cop")
                .putMetadata("bookingId", bookingId.toString())
                .build();

            PaymentIntent intent = PaymentIntent.create(params);

            // Guardamos el registro "PENDING" en la BD con el valor real
            Payment newPayment = new Payment(
                null,
                bookingId,
                amountCop,
                "COP",
                intent.getId(),
                PaymentStatus.PENDING,
                Instant.now()
            );
            paymentPort.save(newPayment);

            return intent.getClientSecret();
        } catch (Exception e) {
            throw new RuntimeException("Error al comunicarse con Stripe", e);
        }
    }
}
