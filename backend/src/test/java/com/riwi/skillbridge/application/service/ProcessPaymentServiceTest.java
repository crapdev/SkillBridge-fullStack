package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
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
    private final BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
    private final ProcessPaymentService service = new ProcessPaymentService(payments, bookings, gateway, users, publisher);

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
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CREATED)));

        service.markPaymentFailed("pi_123");

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(payment.capture());
        assertEquals(PaymentStatus.FAILED, payment.getValue().status());
        verify(bookings, never()).save(any());
    }

    // HU-11: eventos para las notificaciones

    @Test
    void successfulPaymentPublishesBookingPaidWithTheConfirmedBooking() {
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(pending));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CREATED)));

        service.confirmPaymentSuccess("pi_123");

        ArgumentCaptor<Booking> published = ArgumentCaptor.forClass(Booking.class);
        verify(publisher).bookingPaid(published.capture());
        assertEquals(BookingStatus.CONFIRMED, published.getValue().status());
    }

    @Test
    void repeatedWebhookDoesNotPublishBookingPaidAgain() {
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(pending));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));

        service.confirmPaymentSuccess("pi_123");

        verifyNoInteractions(publisher);
    }

    @Test
    void paymentForACancelledBookingDoesNotPublish() {
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(pending));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CANCELLED)));

        service.confirmPaymentSuccess("pi_123");

        verifyNoInteractions(publisher);
    }

    @Test
    void failedPaymentPublishesPaymentFailed() {
        Booking pendingBooking = booking(BookingStatus.CREATED);
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(pending));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(pendingBooking));

        service.markPaymentFailed("pi_123");

        verify(publisher).paymentFailed(pendingBooking);
    }

    @Test
    void paymentAlreadyFailedDoesNotPublishAgain() {
        Payment failed = new Payment(pending.id(), bookingId, pending.amount(), "COP", "pi_123",
            PaymentStatus.FAILED, pending.createdAt());
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(failed));

        service.markPaymentFailed("pi_123");

        verifyNoInteractions(publisher);
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

    @Test
    void oldAttemptPaidWhileAnotherIsPending_cancelsThePendingOneAndRecordsThePayment() {
        // BUG-09: el cliente pagó en una pestaña vieja (pi_123, ya FAILED) mientras había otro intento pendiente
        Payment oldAttempt = withStatus(pending, PaymentStatus.FAILED);
        Payment newerPending = new Payment(UUID.randomUUID(), bookingId, new BigDecimal("85000"), "COP",
            "pi_456", PaymentStatus.PENDING, Instant.now());
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(oldAttempt));
        when(payments.findActiveByBookingId(bookingId)).thenReturn(Optional.of(newerPending));
        when(gateway.cancelPaymentIntent("pi_456")).thenReturn(true);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CREATED)));

        service.confirmPaymentSuccess("pi_123");

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments, times(2)).save(saved.capture());
        // Primero se libera el pendiente (el índice de V11 no admite dos activos) y después se registra el pago
        assertEquals("pi_456", saved.getAllValues().get(0).stripePaymentIntentId());
        assertEquals(PaymentStatus.FAILED, saved.getAllValues().get(0).status());
        assertEquals("pi_123", saved.getAllValues().get(1).stripePaymentIntentId());
        assertEquals(PaymentStatus.PAID, saved.getAllValues().get(1).status());
        verify(bookings).save(any());
    }

    @Test
    void secondChargeForAnAlreadyPaidBooking_isNotRecordedAsASecondPaid() {
        Payment oldAttempt = withStatus(pending, PaymentStatus.FAILED);
        Payment alreadyPaid = new Payment(UUID.randomUUID(), bookingId, new BigDecimal("85000"), "COP",
            "pi_456", PaymentStatus.PAID, Instant.now());
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(oldAttempt));
        when(payments.findActiveByBookingId(bookingId)).thenReturn(Optional.of(alreadyPaid));

        // No lanza error: Stripe reintentaría el webhook sin fin. Queda en el log para el reembolso
        service.confirmPaymentSuccess("pi_123");

        verify(payments, never()).save(any());
        verify(gateway, never()).cancelPaymentIntent(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void repeatedWebhookForTheActivePayment_doesNotTouchOtherPayments() {
        Payment paid = withStatus(pending, PaymentStatus.PAID);
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(paid));
        when(payments.findActiveByBookingId(bookingId)).thenReturn(Optional.of(paid));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));

        service.confirmPaymentSuccess("pi_123");

        verify(gateway, never()).cancelPaymentIntent(any());
        verify(payments).save(any());
    }

    private static Payment withStatus(Payment p, PaymentStatus status) {
        return new Payment(p.id(), p.bookingId(), p.amount(), p.currency(), p.stripePaymentIntentId(), status, p.createdAt());
    }

    private void givenOwnPayment(Payment payment) {
        UUID customerId = UUID.randomUUID();
        when(payments.findByStripeIntentId("pi_123")).thenReturn(Optional.of(payment));
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(customerId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(
            new Booking(bookingId, UUID.randomUUID(), customerId, Instant.now().plusSeconds(86400 * 3), BookingStatus.CREATED)));
    }
}
