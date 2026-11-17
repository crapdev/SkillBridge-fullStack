package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.BookingService;
import com.riwi.skillbridge.domain.exception.SlotAlreadyTakenException;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * BUG-08: dos (o más) clientes que reservan la misma franja al mismo tiempo. Solo uno debe ganar.
 *
 * <p>Usa el {@code BookingService} real con sus adaptadores de PostgreSQL (Testcontainers), así la transacción y el
 * índice único son los de verdad. Para que el choque ocurra siempre, una barrera detiene a todos los hilos justo
 * después de que revisaron que la franja estaba libre y antes de guardar: es el peor caso de una carrera real.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Sin la transacción de la prueba: cada hilo debe ver lo que los demás confirman en la base
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BookingService.class, BookingPersistenceAdapter.class, AvailabilitySlotPersistenceAdapter.class})
class BookingConcurrencyIntegrationTest {

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

    /** Mentoría sembrada por V1 (bookings.offering_id tiene clave foránea a offerings). */
    private static final UUID OFFERING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final int CUSTOMERS = 5;

    @Autowired BookingService bookingService;
    @Autowired JdbcTemplate jdbc;

    @MockitoSpyBean AvailabilitySlotRepositoryPort slots;
    @MockitoSpyBean BookingRepositoryPort bookings;

    @MockitoBean OfferingRepositoryPort offerings;
    @MockitoBean UserAccountPort users;
    @MockitoBean BookingEventPublisherPort events;
    @MockitoBean CompleteFinishedBookingsUseCase completion;
    @MockitoBean OfferingCachePort offeringCache;

    private final Instant scheduledAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private UUID slotId;

    @BeforeEach
    void setUp() {
        clean();
        slotId = UUID.randomUUID();
        jdbc.update("INSERT INTO availability_slots (id, offering_id, scheduled_at, reserved) VALUES (?, ?, ?, false)",
            slotId, OFFERING_ID, java.sql.Timestamp.from(scheduledAt));
        Offering offering = new Offering(OFFERING_ID, UUID.randomUUID(), "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findById(OFFERING_ID)).thenReturn(Optional.of(offering));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM bookings WHERE offering_id = ?", OFFERING_ID);
        jdbc.update("DELETE FROM availability_slots WHERE offering_id = ?", OFFERING_ID);
    }

    private String newCustomer() {
        UUID id = UUID.randomUUID();
        String email = id + "@test.com";
        // bookings.customer_id tiene clave foránea a app_users
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            id, "Cliente", email, "hash-no-importa", "CUSTOMER");
        when(users.findIdByEmail(email)).thenReturn(Optional.of(id));
        return email;
    }

    private int activeBookingsForSlot() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE offering_id = ? AND scheduled_at = ? AND status <> 'CANCELLED'",
            Integer.class, OFFERING_ID, java.sql.Timestamp.from(scheduledAt));
    }

    private boolean slotReserved() {
        return jdbc.queryForObject("SELECT reserved FROM availability_slots WHERE id = ?", Boolean.class, slotId);
    }

    @Test
    void onlyOneOfSeveralSimultaneousBookingsWinsTheSlot() throws Exception {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < CUSTOMERS; i++) emails.add(newCustomer());

        // Todos los hilos llegan aquí después de comprobar que la franja está libre y antes de guardar nada
        CyclicBarrier allChecked = new CyclicBarrier(CUSTOMERS);
        doAnswer(invocation -> {
            allChecked.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(slots).save(any());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CUSTOMERS);
        List<Future<Object>> results = new ArrayList<>();
        for (String email : emails) {
            Callable<Object> attempt = () -> {
                start.await();
                try {
                    return bookingService.create(OFFERING_ID, scheduledAt, email);
                } catch (RuntimeException e) {
                    return e;
                }
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();

        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> result : results) outcomes.add(result.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        long winners = outcomes.stream().filter(o -> !(o instanceof Throwable)).count();
        assertThat(winners).as("solo un cliente debe obtener la franja").isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o instanceof Throwable))
            .as("los demás reciben 'este horario acaba de ser apartado' (409)")
            .hasSize(CUSTOMERS - 1)
            .allMatch(o -> o instanceof SlotAlreadyTakenException);
        assertThat(activeBookingsForSlot()).as("reservas activas guardadas para esa franja").isEqualTo(1);
        assertThat(slotReserved()).isTrue();
    }

    @Test
    void slotIsNotLeftReservedWhenTheBookingCannotBeSaved() {
        // Otra petición ya guardó su reserva, pero esta no la vio (la revisión pasó antes de que la otra guardara)
        String winner = newCustomer();
        UUID winnerId = users.findIdByEmail(winner).orElseThrow();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status, price) VALUES (?, ?, ?, ?, 'CREATED', 85000)",
            UUID.randomUUID(), OFFERING_ID, winnerId, java.sql.Timestamp.from(scheduledAt));
        doReturn(false).when(bookings).existsActiveByOfferingIdAndScheduledAt(any(), any());
        doReturn(false).when(bookings).existsActiveByCustomerIdAndScheduledAt(any(), any());
        String late = newCustomer();

        Throwable error = null;
        try {
            bookingService.create(OFFERING_ID, scheduledAt, late);
        } catch (RuntimeException e) {
            error = e;
        }

        assertThat(error).as("la segunda reserva para la misma franja debe rechazarse")
            .isInstanceOf(SlotAlreadyTakenException.class);
        assertThat(activeBookingsForSlot()).isEqualTo(1);
        assertThat(slotReserved()).as("si la reserva no se guarda, la franja no queda reservada").isFalse();
    }
}
