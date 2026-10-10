package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort.PaymentOutcome;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
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

class ProcessPaymentServiceTest {

    private final PaymentPort payments = mock(PaymentPort.class);
    private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final UserAccountPort users = mock(UserAccountPort.class);
    private final ProcessPaymentService service = new ProcessPaymentService(payments, bookings, gateway, users);

    private final UUID bookingId = UUID.randomUUID();
    private final Payment pending = new Payment(UUID.randomUUID(), bookingId, new BigDecimal("85000"), "COP",
        "pi_123", PaymentStatus.PENDING, Instant.now());

    @Test
    void successfulPaymentMarksPaymentPaidAndConfirmsBooking() {
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(pending));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CREATED)));

        service.confirmPaymentSuccess("pi_123");

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(payment.capture());
        assertEquals(PaymentStatus.PAID, payment.getValue().status());

        ArgumentCaptor<Booking> booking = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(booking.capture());
        assertEquals(BookingStatus.CONFIRMED, booking.getValue().status());
    }

    @Test
    void successfulPaymentDoesNotReviveACancelledBooking() {
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(pending));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CANCELLED)));

        service.confirmPaymentSuccess("pi_123");

        verify(payments).save(any(Payment.class));
        verify(bookings, never()).save(any());
    }

    @Test
    void failedPaymentLeavesBookingPending() {
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(pending));

        service.markPaymentFailed("pi_123");

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(payment.capture());
        assertEquals(PaymentStatus.FAILED, payment.getValue().status());
        verifyNoInteractions(bookings);
    }

    private Booking booking(BookingStatus status) {
        return new Booking(bookingId, UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(86400 * 3), status);
    }

    // ---- Verificación del pago contra la pasarela (cuando no llega el webhook) ----

    private static final String EMAIL = "cliente@example.com";

    @Test
    void syncConfirmsBookingWhenGatewaySaysSucceeded() {
        givenOwnPayment(pending);
        when(gateway.checkOutcome("pi_123")).thenReturn(PaymentOutcome.SUCCEEDED);

        assertEquals(PaymentStatus.PAID, service.syncPayment("pi_123", EMAIL));

        ArgumentCaptor<Booking> booking = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(booking.capture());
        assertEquals(BookingStatus.CONFIRMED, booking.getValue().status());
    }

    @Test
    void syncLeavesPaymentPendingWhileGatewayHasNoResult() {
        givenOwnPayment(pending);
        when(gateway.checkOutcome("pi_123")).thenReturn(PaymentOutcome.PENDING);

        assertEquals(PaymentStatus.PENDING, service.syncPayment("pi_123", EMAIL));

        verify(payments, never()).save(any());
        verify(bookings, never()).save(any());
    }

    @Test
    void syncDoesNotAskGatewayAgainForAProcessedPayment() {
        Payment paid = new Payment(pending.id(), bookingId, pending.amount(), "COP", "pi_123", PaymentStatus.PAID, pending.createdAt());
        givenOwnPayment(paid);

        assertEquals(PaymentStatus.PAID, service.syncPayment("pi_123", EMAIL));

        verifyNoInteractions(gateway);
    }

    @Test
    void syncRejectsSomeoneElsesPayment() {
        givenOwnPayment(pending);
        when(users.findIdByEmail("otro@example.com")).thenReturn(Optional.of(UUID.randomUUID()));

        assertThrows(UnauthorizedActionException.class, () -> service.syncPayment("pi_123", "otro@example.com"));

        verifyNoInteractions(gateway);
    }

    private void givenOwnPayment(Payment payment) {
        UUID customerId = UUID.randomUUID();
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(payment));
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(customerId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(
            new Booking(bookingId, UUID.randomUUID(), customerId, Instant.now().plusSeconds(86400 * 3), BookingStatus.CREATED)));
    }
}
