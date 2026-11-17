package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.ActivePaymentAlreadyExistsException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUG-09: cada vez que el cliente abría la pantalla de pago se creaba un PaymentIntent y un pago PENDING nuevos.
 *
 * <p>Usa una pasarela simulada y un repositorio de pagos en memoria para poder contar cuántos pagos quedan
 * guardados por reserva, como se haría con {@code SELECT COUNT(*) FROM payments WHERE booking_id = ?}.
 */
class CreatePaymentSinglePaymentTest {

    private static final String EMAIL = "cliente@example.com";
    private static final BigDecimal PRICE = new BigDecimal("85000");

    private final InMemoryPayments payments = new InMemoryPayments();
    private final SimulatedGateway gateway = new SimulatedGateway();
    private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
    private final UserAccountPort users = mock(UserAccountPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private final CreatePaymentService service =
        new CreatePaymentService(payments, gateway, bookings, users, clock);

    private final UUID customerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(customerId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(
            new Booking(bookingId, offeringId, customerId, Instant.parse("2026-11-20T15:00:00Z"), BookingStatus.CREATED, PRICE)));
    }

    @Test
    void openingThePaymentTwice_keepsASinglePaymentForTheBooking() {
        String first = service.createPaymentIntent(bookingId, EMAIL);
        String second = service.createPaymentIntent(bookingId, EMAIL);

        assertThat(payments.forBooking(bookingId)).hasSize(1);
        assertThat(payments.forBooking(bookingId).get(0).status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(gateway.createdIntents()).isEqualTo(1);
        // El segundo intento reutiliza el mismo cobro de Stripe
        assertThat(second).isEqualTo(first);
    }

    @Test
    void retryingAfterAFailedPayment_startsANewPayment() {
        service.createPaymentIntent(bookingId, EMAIL);
        Payment firstAttempt = payments.forBooking(bookingId).get(0);
        // El webhook payment_intent.payment_failed marca el intento como FAILED
        payments.save(withStatus(firstAttempt, PaymentStatus.FAILED));

        String retry = service.createPaymentIntent(bookingId, EMAIL);

        assertThat(payments.forBooking(bookingId))
            .extracting(Payment::status)
            .containsExactly(PaymentStatus.FAILED, PaymentStatus.PENDING);
        assertThat(retry).isNotEqualTo(gateway.secretOf(firstAttempt.stripePaymentIntentId()));
        // El cobro rechazado seguía aceptando otra tarjeta en Stripe: se cancela para que solo quede uno pagable
        assertThat(gateway.stateOf(firstAttempt.stripePaymentIntentId())).isEqualTo(PaymentGatewayPort.IntentState.CANCELED);
        assertThat(gateway.stateOf(payments.forBooking(bookingId).get(1).stripePaymentIntentId()))
            .isEqualTo(PaymentGatewayPort.IntentState.AWAITING_PAYMENT);
    }

    @Test
    void rejectedChargeRetriedInTheOldTab_isBeingPaid_doesNotStartAnotherCharge() {
        service.createPaymentIntent(bookingId, EMAIL);
        Payment firstAttempt = payments.forBooking(bookingId).get(0);
        payments.save(withStatus(firstAttempt, PaymentStatus.FAILED));
        // El cliente reintentó con otra tarjeta en la pestaña vieja y Stripe lo está procesando
        gateway.setState(firstAttempt.stripePaymentIntentId(), PaymentGatewayPort.IntentState.PROCESSING);

        assertThatThrownBy(() -> service.createPaymentIntent(bookingId, EMAIL))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Ya recibimos un pago");

        assertThat(gateway.createdIntents()).isEqualTo(1);
        assertThat(payments.forBooking(bookingId)).extracting(Payment::status).containsExactly(PaymentStatus.FAILED);
    }

    // Antes de BUG-10 el pago cobraba el precio actual de la mentoría: un cobro abierto en ese entonces puede tener
    // otro monto que el precio guardado en la reserva. No se reutiliza: se cancela y se cobra el de la reserva.
    @Test
    void openChargeFromBeforeBug10WithAnotherAmount_isReplacedByTheBookingPrice() {
        BigDecimal oldCatalogPrice = new BigDecimal("120000");
        String oldIntent = gateway.createPaymentIntent(bookingId, oldCatalogPrice, "COP").id();
        payments.save(new Payment(null, bookingId, oldCatalogPrice, "COP", oldIntent, PaymentStatus.PENDING, clock.instant()));

        service.createPaymentIntent(bookingId, EMAIL);

        assertThat(gateway.stateOf(oldIntent)).isEqualTo(PaymentGatewayPort.IntentState.CANCELED);
        assertThat(payments.forBooking(bookingId))
            .extracting(Payment::status, Payment::amount)
            .containsExactly(
                // CANCELED y no FAILED: el cobro no se rechazó, se reemplazó por uno con el precio de la reserva
                org.assertj.core.groups.Tuple.tuple(PaymentStatus.CANCELED, oldCatalogPrice),
                org.assertj.core.groups.Tuple.tuple(PaymentStatus.PENDING, PRICE));
    }

    @Test
    void chargeCanceledInStripe_startsANewPayment() {
        service.createPaymentIntent(bookingId, EMAIL);
        String oldIntent = payments.forBooking(bookingId).get(0).stripePaymentIntentId();
        gateway.setState(oldIntent, PaymentGatewayPort.IntentState.CANCELED);

        service.createPaymentIntent(bookingId, EMAIL);

        assertThat(payments.forBooking(bookingId))
            .extracting(Payment::status)
            .containsExactly(PaymentStatus.CANCELED, PaymentStatus.PENDING);
    }

    @Test
    void stripeAlreadyChargedButTheWebhookHasNotArrived_doesNotChargeAgain() {
        service.createPaymentIntent(bookingId, EMAIL);
        String intent = payments.forBooking(bookingId).get(0).stripePaymentIntentId();
        gateway.setState(intent, PaymentGatewayPort.IntentState.SUCCEEDED);

        assertThatThrownBy(() -> service.createPaymentIntent(bookingId, EMAIL))
            .isInstanceOf(BusinessRuleException.class);

        assertThat(gateway.createdIntents()).isEqualTo(1);
        assertThat(payments.forBooking(bookingId)).extracting(Payment::status).containsExactly(PaymentStatus.PENDING);
    }

    @Test
    void bookingAlreadyPaid_doesNotStartAnotherPayment() {
        service.createPaymentIntent(bookingId, EMAIL);
        payments.save(withStatus(payments.forBooking(bookingId).get(0), PaymentStatus.PAID));

        assertThatThrownBy(() -> service.createPaymentIntent(bookingId, EMAIL))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ya fue pagada");

        assertThat(gateway.createdIntents()).isEqualTo(1);
    }

    @Test
    void twoRequestsAtTheSameTime_theLoserCancelsItsChargeAndUsesTheWinners() {
        // Otra petición de la misma reserva guarda su pago justo después de que esta revisó y antes de que guarde
        Payment[] winner = new Payment[1];
        gateway.beforeReturningNewIntent(() -> {
            String winnerIntent = gateway.createPaymentIntent(bookingId, PRICE, "COP").id();
            winner[0] = payments.save(new Payment(null, bookingId, PRICE, "COP", winnerIntent,
                PaymentStatus.PENDING, clock.instant()));
        });

        String secret = service.createPaymentIntent(bookingId, EMAIL);

        assertThat(payments.forBooking(bookingId)).containsExactly(winner[0]);
        assertThat(secret).isEqualTo(gateway.secretOf(winner[0].stripePaymentIntentId()));
        // El cobro que creó la petición perdedora queda cancelado en Stripe
        assertThat(gateway.canceledIntents()).hasSize(1).doesNotContain(winner[0].stripePaymentIntentId());
    }

    private static Payment withStatus(Payment p, PaymentStatus status) {
        return new Payment(p.id(), p.bookingId(), p.amount(), p.currency(), p.stripePaymentIntentId(), status, p.createdAt());
    }

    /** Tabla payments en memoria (en orden de creación), con el índice único de V11. */
    static class InMemoryPayments implements PaymentPort {
        private static final EnumSet<PaymentStatus> ACTIVE = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PAID);
        private final Map<UUID, Payment> rows = new LinkedHashMap<>();

        @Override
        public Payment save(Payment payment) {
            UUID id = payment.id() != null ? payment.id() : UUID.randomUUID();
            boolean anotherActive = rows.values().stream().anyMatch(p -> !p.id().equals(id)
                && p.bookingId().equals(payment.bookingId()) && ACTIVE.contains(p.status()));
            if (ACTIVE.contains(payment.status()) && anotherActive) {
                throw new ActivePaymentAlreadyExistsException();
            }
            Payment saved = new Payment(id, payment.bookingId(), payment.amount(), payment.currency(),
                payment.stripePaymentIntentId(), payment.status(), payment.createdAt());
            rows.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Payment> findByStripeIntentId(String intentId) {
            return rows.values().stream().filter(p -> p.stripePaymentIntentId().equals(intentId)).findFirst();
        }

        @Override
        public Optional<Payment> findActiveByBookingId(UUID bookingId) {
            return forBooking(bookingId).stream().filter(p -> ACTIVE.contains(p.status())).findFirst();
        }

        @Override
        public Optional<Payment> findLatestFailedByBookingId(UUID bookingId) {
            List<Payment> failed = forBooking(bookingId).stream().filter(p -> p.status() == PaymentStatus.FAILED).toList();
            return failed.isEmpty() ? Optional.empty() : Optional.of(failed.get(failed.size() - 1));
        }

        List<Payment> forBooking(UUID bookingId) {
            return rows.values().stream().filter(p -> p.bookingId().equals(bookingId)).toList();
        }
    }

    /** Stripe simulado: cada cobro creado queda esperando el pago hasta que la prueba cambie su estado. */
    static class SimulatedGateway implements PaymentGatewayPort {
        private final Map<String, IntentState> intents = new LinkedHashMap<>();
        private Runnable beforeReturningNewIntent;

        @Override
        public PaymentIntent createPaymentIntent(UUID bookingId, BigDecimal amount, String currency) {
            String id = "pi_" + (intents.size() + 1);
            intents.put(id, IntentState.AWAITING_PAYMENT);
            Runnable hook = beforeReturningNewIntent;
            beforeReturningNewIntent = null;
            if (hook != null) {
                hook.run();
            }
            return new PaymentIntent(id, secretOf(id));
        }

        @Override
        public PaymentOutcome checkOutcome(String paymentIntentId) {
            return PaymentOutcome.PENDING;
        }

        @Override
        public ExistingPaymentIntent retrievePaymentIntent(String paymentIntentId) {
            return new ExistingPaymentIntent(new PaymentIntent(paymentIntentId, secretOf(paymentIntentId)),
                stateOf(paymentIntentId));
        }

        @Override
        public boolean cancelPaymentIntent(String paymentIntentId) {
            IntentState state = stateOf(paymentIntentId);
            if (state == IntentState.SUCCEEDED || state == IntentState.PROCESSING) {
                return false;
            }
            intents.put(paymentIntentId, IntentState.CANCELED);
            return true;
        }

        void beforeReturningNewIntent(Runnable hook) {
            this.beforeReturningNewIntent = hook;
        }

        void setState(String intentId, IntentState state) {
            intents.put(intentId, state);
        }

        IntentState stateOf(String intentId) {
            return intents.get(intentId);
        }

        int createdIntents() {
            return intents.size();
        }

        List<String> canceledIntents() {
            return intents.entrySet().stream()
                .filter(e -> e.getValue() == IntentState.CANCELED).map(Map.Entry::getKey).toList();
        }

        String secretOf(String intentId) {
            return intentId + "_secret";
        }
    }
}
