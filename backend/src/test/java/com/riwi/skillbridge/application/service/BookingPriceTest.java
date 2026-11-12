package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BUG-10: la reserva no guardaba el precio. Si el proveedor cambiaba el precio de la mentoría después de que el
 * cliente reservó, el pago cobraba el precio nuevo.
 *
 * <p>Recorre el flujo real: {@code BookingService} crea la reserva, el proveedor sube el precio y
 * {@code CreatePaymentService} prepara el cobro. Las reservas se guardan en memoria para que el pago lea lo mismo
 * que se guardó al reservar.
 */
class BookingPriceTest {

    private static final String EMAIL = "cliente@example.com";
    private static final BigDecimal PRICE_WHEN_BOOKED = new BigDecimal("100000");
    private static final BigDecimal NEW_PRICE = new BigDecimal("120000");

    private final Map<UUID, Booking> storedBookings = new HashMap<>();
    private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
    private final OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
    private final UserAccountPort users = mock(UserAccountPort.class);
    private final AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);
    private final PaymentPort payments = mock(PaymentPort.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);

    private final UUID offeringId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final Instant scheduledAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

    private BookingService bookingService;
    private CreatePaymentService paymentService;

    @BeforeEach
    void setUp() {
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            storedBookings.put(booking.id(), booking);
            return booking;
        });
        when(bookings.findById(any(UUID.class))).thenAnswer(invocation ->
            Optional.ofNullable(storedBookings.get(invocation.<UUID>getArgument(0))));
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(customerId));
        when(slots.findByOfferingIdAndScheduledAt(offeringId, scheduledAt))
            .thenReturn(Optional.of(new AvailabilitySlot(UUID.randomUUID(), offeringId, scheduledAt, false)));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.createPaymentIntent(any(), any(), anyString()))
            .thenReturn(new PaymentGatewayPort.PaymentIntent("pi_1", "pi_1_secret"));

        bookingService = new BookingService(bookings, offerings, users, mock(BookingEventPublisherPort.class), slots,
            mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));
        paymentService = new CreatePaymentService(payments, gateway, bookings, users, clock);
    }

    private void offeringPriceIs(BigDecimal price) {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
            new Offering(offeringId, providerId, "Kubernetes", "Primeros pasos", "CLOUD", price, true)));
    }

    @Test
    void changingTheOfferingPriceAfterBooking_chargesThePriceTheCustomerBookedAt() {
        offeringPriceIs(PRICE_WHEN_BOOKED);
        Booking booking = bookingService.create(offeringId, scheduledAt, EMAIL);

        // El proveedor edita la mentoría (HU-19) y sube el precio
        offeringPriceIs(NEW_PRICE);
        paymentService.createPaymentIntent(booking.id(), EMAIL);

        verify(gateway).createPaymentIntent(eq(booking.id()), eq(PRICE_WHEN_BOOKED), anyString());
    }

    @Test
    void theBookingKeepsThePriceItWasMadeAt() {
        offeringPriceIs(PRICE_WHEN_BOOKED);
        Booking booking = bookingService.create(offeringId, scheduledAt, EMAIL);

        offeringPriceIs(NEW_PRICE);

        assertThat(storedBookings.get(booking.id()).price()).isEqualByComparingTo(PRICE_WHEN_BOOKED);
    }

    @Test
    void cancellingOrConfirmingTheBooking_keepsItsPrice() {
        offeringPriceIs(PRICE_WHEN_BOOKED);
        Booking booking = bookingService.create(offeringId, scheduledAt, EMAIL);

        assertThat(booking.confirm().price()).isEqualByComparingTo(PRICE_WHEN_BOOKED);
        assertThat(booking.cancel(Instant.now()).price()).isEqualByComparingTo(PRICE_WHEN_BOOKED);
    }
}
