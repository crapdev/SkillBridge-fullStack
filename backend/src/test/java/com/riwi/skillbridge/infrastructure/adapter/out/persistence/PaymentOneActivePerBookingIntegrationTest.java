package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort.ExistingPaymentIntent;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort.IntentState;
import com.riwi.skillbridge.application.port.out.PaymentGatewayPort.PaymentIntent;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.CreatePaymentService;
import com.riwi.skillbridge.domain.exception.ActivePaymentAlreadyExistsException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BUG-09: una reserva solo puede tener un pago activo (PENDING o PAID), garantizado por el índice
 * {@code uq_payments_active_booking} de V11 en PostgreSQL real (Testcontainers).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Sin la transacción de la prueba: cada hilo debe ver lo que los demás confirman en la base
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CreatePaymentService.class, PaymentPersistenceAdapter.class, BookingPersistenceAdapter.class,
    PaymentOneActivePerBookingIntegrationTest.FixedClock.class})
class PaymentOneActivePerBookingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("skillbridge_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    /** Mentoría sembrada por V1 (bookings.offering_id tiene clave foránea a offerings). */
    private static final UUID OFFERING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final BigDecimal PRICE = new BigDecimal("85000.00");
    private static final int ATTEMPTS = 5;

    @Autowired CreatePaymentService createPaymentService;
    @Autowired PaymentPort paymentPort;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean PaymentGatewayPort gateway;
    @MockitoBean OfferingRepositoryPort offerings;
    @MockitoBean UserAccountPort users;

    private final AtomicInteger intentCounter = new AtomicInteger();
    private UUID customerId;
    private String email;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        clean();
        customerId = UUID.randomUUID();
        email = customerId + "@test.com";
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            customerId, "Cliente", email, "hash-no-importa", "CUSTOMER");
        bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status) VALUES (?, ?, ?, ?, 'CREATED')",
            bookingId, OFFERING_ID, customerId, Timestamp.from(Instant.now().plus(5, ChronoUnit.DAYS)));

        when(users.findIdByEmail(email)).thenReturn(Optional.of(customerId));
        when(offerings.findById(OFFERING_ID)).thenReturn(Optional.of(
            new Offering(OFFERING_ID, UUID.randomUUID(), "Java", "Mentoría", "BACKEND", PRICE, true)));
        // Stripe simulado: cada cobro nuevo tiene su propio ID y todos siguen esperando el pago
        when(gateway.createPaymentIntent(any(), any(), anyString())).thenAnswer(invocation -> newIntent());
        when(gateway.retrievePaymentIntent(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return new ExistingPaymentIntent(new PaymentIntent(id, id + "_secret"), IntentState.AWAITING_PAYMENT);
        });
        when(gateway.cancelPaymentIntent(anyString())).thenReturn(true);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM payments WHERE booking_id IN (SELECT id FROM bookings WHERE offering_id = ?)", OFFERING_ID);
        jdbc.update("DELETE FROM bookings WHERE offering_id = ?", OFFERING_ID);
    }

    private PaymentIntent newIntent() {
        String id = "pi_" + intentCounter.incrementAndGet() + "_" + UUID.randomUUID();
        return new PaymentIntent(id, id + "_secret");
    }

    private List<String> paymentStatuses() {
        return jdbc.queryForList("SELECT status FROM payments WHERE booking_id = ? ORDER BY created_at", String.class, bookingId);
    }

    private Payment payment(String intentId, PaymentStatus status) {
        return new Payment(null, bookingId, PRICE, "COP", intentId, status, Instant.now());
    }

    @Test
    void openingThePaymentSeveralTimes_leavesOnePendingPaymentInTheDatabase() {
        String first = createPaymentService.createPaymentIntent(bookingId, email);
        String second = createPaymentService.createPaymentIntent(bookingId, email);
        String third = createPaymentService.createPaymentIntent(bookingId, email);

        assertThat(paymentStatuses()).containsExactly("PENDING");
        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
    }

    @Test
    void databaseRejectsASecondPendingPaymentForTheSameBooking() {
        paymentPort.save(payment("pi_a", PaymentStatus.PENDING));

        assertThatThrownBy(() -> paymentPort.save(payment("pi_b", PaymentStatus.PENDING)))
            .isInstanceOf(ActivePaymentAlreadyExistsException.class);
        assertThat(paymentStatuses()).containsExactly("PENDING");
    }

    @Test
    void databaseRejectsTwoPaidPaymentsForTheSameBooking() {
        paymentPort.save(payment("pi_a", PaymentStatus.PAID));
        Payment failed = paymentPort.save(payment("pi_b", PaymentStatus.FAILED));

        Payment paidAgain = new Payment(failed.id(), failed.bookingId(), failed.amount(), failed.currency(),
            failed.stripePaymentIntentId(), PaymentStatus.PAID, failed.createdAt());
        assertThatThrownBy(() -> paymentPort.save(paidAgain))
            .isInstanceOf(ActivePaymentAlreadyExistsException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id = ? AND status = 'PAID'",
            Integer.class, bookingId)).isEqualTo(1);
    }

    @Test
    void failedAttemptsDoNotBlockARetry() {
        paymentPort.save(payment("pi_a", PaymentStatus.FAILED));
        paymentPort.save(payment("pi_b", PaymentStatus.FAILED));

        createPaymentService.createPaymentIntent(bookingId, email);

        assertThat(paymentStatuses()).containsExactly("FAILED", "FAILED", "PENDING");
        // Solo se cancela en Stripe el cobro del último intento rechazado: cada uno anterior ya se canceló al abrir el siguiente
        verify(gateway).cancelPaymentIntent("pi_b");
        verify(gateway, never()).cancelPaymentIntent("pi_a");
    }

    @Test
    void severalSimultaneousAttempts_keepOnePaymentAndAllGetTheSameCharge() throws Exception {
        // Todos los hilos llegan aquí después de comprobar que no había pago activo y antes de guardar
        CyclicBarrier allChecked = new CyclicBarrier(ATTEMPTS);
        doAnswer(invocation -> {
            allChecked.await(10, TimeUnit.SECONDS);
            return newIntent();
        }).when(gateway).createPaymentIntent(any(), any(), anyString());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            Callable<Object> attempt = () -> {
                start.await();
                try {
                    return createPaymentService.createPaymentIntent(bookingId, email);
                } catch (RuntimeException e) {
                    return e;
                }
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();

        List<Object> secrets = new ArrayList<>();
        for (Future<Object> result : results) secrets.add(result.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(paymentStatuses()).as("pagos guardados para la reserva").containsExactly("PENDING");
        String winnerIntent = jdbc.queryForObject(
            "SELECT stripe_payment_intent_id FROM payments WHERE booking_id = ?", String.class, bookingId);
        assertThat(secrets).as("todas las peticiones usan el cobro que quedó guardado")
            .hasSize(ATTEMPTS)
            .containsOnly(winnerIntent + "_secret");
        // Los cobros que crearon las peticiones perdedoras se cancelan en Stripe
        verify(gateway, times(ATTEMPTS - 1)).cancelPaymentIntent(anyString());
    }
}
