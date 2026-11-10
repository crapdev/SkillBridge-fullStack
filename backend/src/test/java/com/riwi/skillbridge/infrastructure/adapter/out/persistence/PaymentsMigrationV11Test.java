package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUG-09: V11 limpia los pagos duplicados que ya existían antes de crear el índice único.
 * Aplica las migraciones hasta V10, siembra datos sucios como los que dejaba el bug y después aplica V11.
 */
@Testcontainers
class PaymentsMigrationV11Test {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("skillbridge_test")
        .withUsername("test")
        .withPassword("test");

    private static final UUID OFFERING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private JdbcTemplate jdbc;
    private UUID customerId;

    @BeforeEach
    void resetDatabase() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        flyway("10").migrate();

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

    private UUID booking(int daysAhead) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status) VALUES (?, ?, ?, ?, 'CREATED')",
            id, OFFERING_ID, customerId, Timestamp.from(Instant.now().plus(daysAhead, ChronoUnit.DAYS)));
        return id;
    }

    private void payment(UUID bookingId, String intent, String status, int minutesAgo) {
        jdbc.update("INSERT INTO payments (id, booking_id, amount, currency, stripe_payment_intent_id, status, created_at) "
                + "VALUES (?, ?, 85000, 'COP', ?, ?, ?)",
            UUID.randomUUID(), bookingId, intent, status, Timestamp.from(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES)));
    }

    private String statusOf(String intent) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE stripe_payment_intent_id = ?", String.class, intent);
    }

    @Test
    void keepsThePaidOneOrTheNewestPendingAndFailsTheRest() {
        UUID onlyPending = booking(3);
        payment(onlyPending, "pi_viejo", "PENDING", 30);
        payment(onlyPending, "pi_medio", "PENDING", 20);
        payment(onlyPending, "pi_nuevo", "PENDING", 10);

        UUID paid = booking(4);
        payment(paid, "pi_pendiente_1", "PENDING", 30);
        payment(paid, "pi_pagado", "PAID", 20);
        payment(paid, "pi_pendiente_2", "PENDING", 10);
        payment(paid, "pi_rechazado", "FAILED", 5);

        flyway("11").migrate();

        assertThat(statusOf("pi_viejo")).isEqualTo("FAILED");
        assertThat(statusOf("pi_medio")).isEqualTo("FAILED");
        assertThat(statusOf("pi_nuevo")).as("el último que abrió el cliente").isEqualTo("PENDING");
        assertThat(statusOf("pi_pagado")).isEqualTo("PAID");
        assertThat(statusOf("pi_pendiente_1")).isEqualTo("FAILED");
        assertThat(statusOf("pi_pendiente_2")).isEqualTo("FAILED");
        assertThat(statusOf("pi_rechazado")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_payments_active_booking'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void stopsWhenABookingWasChargedTwice() {
        UUID doubleCharged = booking(3);
        payment(doubleCharged, "pi_pago_1", "PAID", 20);
        payment(doubleCharged, "pi_pago_2", "PAID", 10);

        // Un cobro doble necesita revisión (y reembolso): la migración no decide cuál de los dos pagos vale
        assertThatThrownBy(() -> flyway("11").migrate())
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining("más de un pago PAID");
        assertThat(statusOf("pi_pago_1")).isEqualTo("PAID");
        assertThat(statusOf("pi_pago_2")).isEqualTo("PAID");
    }
}
