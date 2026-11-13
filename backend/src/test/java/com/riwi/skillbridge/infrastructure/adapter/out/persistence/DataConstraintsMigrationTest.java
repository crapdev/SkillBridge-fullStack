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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUG-11: la base de datos no debe aceptar datos imposibles: mentorías sin proveedor ni estados que no existen.
 * Parte de una base vacía y aplica todas las migraciones, como un despliegue nuevo (make reset).
 */
@Testcontainers
class DataConstraintsMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("skillbridge_test")
        .withUsername("test")
        .withPassword("test");

    /** Mentorías de ejemplo que siembra V1. */
    private static final String SAMPLE_OFFERINGS =
        "'11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333'";
    private static final UUID OFFERING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String DEMO_PROVIDER = "proveedor.demo@skillbridge.dev";

    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(target)
            .load();
    }

    private UUID customer() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, 'CUSTOMER')",
            id, "Cliente", id + "@test.com", "hash-no-importa");
        return id;
    }

    private UUID booking(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status, price) VALUES (?, ?, ?, ?, ?, 85000)",
            id, OFFERING_ID, customer(), Timestamp.from(Instant.now().plus(3, ChronoUnit.DAYS)), status);
        return id;
    }

    private void payment(UUID bookingId, String status) {
        jdbc.update("INSERT INTO payments (id, booking_id, amount, currency, stripe_payment_intent_id, status) "
                + "VALUES (?, ?, 85000, 'COP', ?, ?)",
            UUID.randomUUID(), bookingId, "pi_" + UUID.randomUUID(), status);
    }

    @Test
    void sampleOfferingsBelongToTheDemoProvider() {
        flyway("latest").migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM offerings WHERE provider_id IS NULL", Integer.class))
            .as("mentorías sin proveedor").isZero();
        assertThat(jdbc.queryForList(
            "SELECT u.email FROM offerings o JOIN app_users u ON u.id = o.provider_id WHERE o.id IN (" + SAMPLE_OFFERINGS + ")",
            String.class))
            .hasSize(3).containsOnly(DEMO_PROVIDER);
    }

    @Test
    void theExistingDemoProviderFromV4IsReused() {
        flyway("11").migrate();
        UUID demoId = jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, DEMO_PROVIDER);

        flyway("latest").migrate();

        assertThat(jdbc.queryForList("SELECT DISTINCT provider_id FROM offerings WHERE id IN (" + SAMPLE_OFFERINGS + ")",
            UUID.class)).containsExactly(demoId);
        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM app_users WHERE email = ?", Integer.class, DEMO_PROVIDER))
            .isEqualTo(1);
    }

    @Test
    void ifTheDemoProviderWasDeleted_itIsRecreatedWithoutAccess() {
        flyway("11").migrate();
        jdbc.update("DELETE FROM app_users WHERE email = ?", DEMO_PROVIDER);

        flyway("latest").migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM offerings WHERE provider_id IS NULL", Integer.class)).isZero();
        String password = jdbc.queryForObject("SELECT password FROM app_users WHERE email = ?", String.class, DEMO_PROVIDER);
        assertThat(password).as("no es un hash BCrypt: nadie puede iniciar sesión").doesNotStartWith("$2");
    }

    @Test
    void anOfferingWithoutProviderIsRejected() {
        flyway("latest").migrate();

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO offerings (id, title, description, category, price) VALUES (?, 'Sin dueño', 'x', 'BACKEND', 1000)",
            UUID.randomUUID()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInventedBookingStatusIsRejected() {
        flyway("latest").migrate();

        assertThatThrownBy(() -> booking("PAGADA")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anInventedPaymentStatusIsRejected() {
        flyway("latest").migrate();
        UUID bookingId = booking("CREATED");

        assertThatThrownBy(() -> payment(bookingId, "APROBADO")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void everyRealStatusIsStillAccepted() {
        flyway("latest").migrate();

        for (String status : new String[] {"CREATED", "CONFIRMED", "CANCELLED", "COMPLETED"}) {
            booking(status);
        }
        UUID bookingId = booking("CREATED");
        // Un solo pago activo por reserva (BUG-09): primero los que no cuentan y al final uno activo
        for (String status : new String[] {"FAILED", "CANCELED", "PENDING"}) {
            payment(bookingId, status);
        }
        payment(booking("CONFIRMED"), "PAID");

        assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM payments", Integer.class)).isEqualTo(4);
    }
}
