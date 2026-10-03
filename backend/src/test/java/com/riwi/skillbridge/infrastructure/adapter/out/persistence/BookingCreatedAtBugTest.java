package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduce el bug: BookingPersistenceAdapter.save() usa Instant.now() como created_at
 * en cada guardado, así que al re-guardar una reserva existente (p. ej. al cancelarla)
 * se pierde la fecha de creación original.
 *
 * Estado esperado ANTES de corregir el bug: este test FALLA.
 * Estado esperado DESPUÉS de corregirlo:   este test PASA.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingCreatedAtBugTest {

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

    @Autowired JpaBookingRepository jpa;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    // Uno de los 3 servicios que Flyway inserta en V1__init.sql
    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void reSavingABooking_shouldKeepItsOriginalCreatedAt() throws Exception {
        // 1. Preparar: un usuario real (bookings.customer_id tiene clave foránea a app_users)
        UUID customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            customerId, "Tester", "tester-bug1@test.com", "hash-no-importa", "CUSTOMER");

        var adapter = new BookingPersistenceAdapter(jpa);
        UUID bookingId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(7, ChronoUnit.DAYS);

        // 2. Primer guardado: es la creación de la reserva (INSERT)
        adapter.save(new Booking(bookingId, OFFERING_ID, customerId, scheduledAt, BookingStatus.CREATED));
        em.flush();
        em.clear(); // simula que el segundo guardado llega después, en otra operación

        Timestamp createdAtOriginal = jdbc.queryForObject(
            "SELECT created_at FROM bookings WHERE id = ?", Timestamp.class, bookingId);

        // 3. Pasa el tiempo (en la vida real: horas o días entre crear y cancelar)
        Thread.sleep(200);

        // 4. Segundo guardado del MISMO id con otro estado: lo que haría "cancelar" (UPDATE)
        adapter.save(new Booking(bookingId, OFFERING_ID, customerId, scheduledAt, BookingStatus.CANCELLED));
        em.flush();
        em.clear();

        Timestamp createdAtDespues = jdbc.queryForObject(
            "SELECT created_at FROM bookings WHERE id = ?", Timestamp.class, bookingId);
        String statusDespues = jdbc.queryForObject(
            "SELECT status FROM bookings WHERE id = ?", String.class, bookingId);

        // 5. El estado sí cambió (esto confirma que el UPDATE ocurrió)...
        assertThat(statusDespues).isEqualTo("CANCELLED");

        // ...pero la fecha de creación NO debería haber cambiado
        assertThat(createdAtDespues)
            .as("created_at original=%s, después de re-guardar=%s", createdAtOriginal, createdAtDespues)
            .isEqualTo(createdAtOriginal);
    }
}
