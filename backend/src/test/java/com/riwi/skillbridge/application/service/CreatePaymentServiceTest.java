package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreatePaymentServiceTest {

    private final PaymentPort payments = mock(PaymentPort.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
    private final OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
    private final CreatePaymentService service = new CreatePaymentService(payments, gateway, bookings, offerings);

    @Test
    void shouldChargeCatalogPriceAndSavePendingPayment() {
        UUID bookingId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        BigDecimal price = new BigDecimal("85000");

        when(bookings.findById(bookingId)).thenReturn(Optional.of(
            new Booking(bookingId, offeringId, UUID.randomUUID(), Instant.now().plusSeconds(3600), BookingStatus.CREATED)));
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
            new Offering(offeringId, UUID.randomUUID(), "Java", "Mentoría", "BACKEND", price, true)));
        when(gateway.createPaymentIntent(bookingId, price, "COP"))
            .thenReturn(new PaymentGatewayPort.PaymentIntent("pi_123", "pi_123_secret"));

        String clientSecret = service.createPaymentIntent(bookingId);

        assertEquals("pi_123_secret", clientSecret);
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertEquals(bookingId, saved.getValue().bookingId());
        assertEquals(price, saved.getValue().amount());
        assertEquals("pi_123", saved.getValue().stripePaymentIntentId());
        assertEquals(PaymentStatus.PENDING, saved.getValue().status());
    }

    @Test
    void shouldNotCallGatewayWhenBookingDoesNotExist() {
        UUID bookingId = UUID.randomUUID();
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.createPaymentIntent(bookingId));

        verifyNoInteractions(gateway);
        verify(payments, never()).save(any());
    }
}
