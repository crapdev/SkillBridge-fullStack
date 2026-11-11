package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUG-10: V12 agrega bookings.price y lo llena para las reservas que ya existían.
 * Aplica las migraciones hasta V11, siembra reservas como las que había antes del arreglo y después aplica V12.
 */
@Testcontainers
class BookingsPriceMigrationV12Test {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("skillbridge_test")
        .withUsername("test")
        .withPassword("test");

    /** Mentoría sembrada por V1. */
    private static final UUID OFFERING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final BigDecimal CURRENT_PRICE = new BigDecimal("120000.00");

    private JdbcTemplate jdbc;
    private UUID customerId;

    @BeforeEach
    void resetDatabase() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        flyway("11").migrate();

        // El proveedor ya cambió el precio de la mentoría: hoy vale 120.000
        jdbc.update("UPDATE offerings SET price = ? WHERE id = ?", CURRENT_PRICE, OFFERING_ID);
        customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, 'CUSTOMER')",
            customerId, "Cliente", customerId + "@test.com", "hash-no-importa");
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(target)
            .load();
    }

    private UUID booking(String status, int daysAhead) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status) VALUES (?, ?, ?, ?, ?)",
            id, OFFERING_ID, customerId, Timestamp.from(Instant.now().plus(daysAhead, ChronoUnit.DAYS)), status);
        return id;
    }

    private void payment(UUID bookingId, String amount, String status) {
        jdbc.update("INSERT INTO payments (id, booking_id, amount, currency, stripe_payment_intent_id, status) "
                + "VALUES (?, ?, ?, 'COP', ?, ?)",
            UUID.randomUUID(), bookingId, new BigDecimal(amount), "pi_" + UUID.randomUUID(), status);
    }

    private BigDecimal priceOf(UUID bookingId) {
        return jdbc.queryForObject("SELECT price FROM bookings WHERE id = ?", BigDecimal.class, bookingId);
    }

    @Test
    void existingBookingsGetThePriceThatWasChargedOrTheCurrentOne() {
        // Se pagó a 100.000 antes de que el proveedor subiera el precio
        UUID paidBefore = booking("CONFIRMED", 3);
        payment(paidBefore, "100000", "PAID");
        // Un intento rechazado no cuenta: no es lo que se cobró
        UUID onlyRejected = booking("CREATED", 4);
        payment(onlyRejected, "100000", "FAILED");
        UUID neverPaid = booking("CREATED", 5);

        flyway("12").migrate();

        assertThat(priceOf(paidBefore)).as("lo que de verdad se cobró").isEqualByComparingTo("100000");
        assertThat(priceOf(onlyRejected)).isEqualByComparingTo(CURRENT_PRICE);
        assertThat(priceOf(neverPaid)).isEqualByComparingTo(CURRENT_PRICE);
    }

    @Test
    void afterTheMigrationEveryBookingMustHaveAPrice() {
        flyway("12").migrate();

        assertThatThrownBy(() -> booking("CREATED", 3))
            .as("price es obligatorio desde V12")
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
