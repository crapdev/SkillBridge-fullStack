package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de regresión: una reserva puede tener varios intentos de pago (abrir el checkout dos veces,
 * reintentar tras un rechazo). Con el antiguo @OneToOne, Hibernate fallaba con
 * "More than one row with the given identifier was found" y Mis reservas respondía 500.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingWithSeveralPaymentsTest {

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

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void bookingWithTwoPaymentAttempts_canStillBeListed() {
        UUID customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            customerId, "Tester", "tester-pagos@test.com", "hash-no-importa", "CUSTOMER");

        UUID bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status) VALUES (?, ?, ?, ?, ?)",
            bookingId, OFFERING_ID, customerId, Timestamp.from(Instant.now().plus(3, ChronoUnit.DAYS)), "CREATED");

        // Dos intentos de pago para la misma reserva
        for (String intent : List.of("pi_intento_1", "pi_intento_2")) {
            jdbc.update("INSERT INTO payments (id, booking_id, amount, currency, stripe_payment_intent_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), bookingId, 85000, "COP", intent, "PENDING");
        }
        em.clear();

        List<BookingEntity> bookings = jpa.findByCustomerIdOrderByScheduledAtDesc(customerId);

        assertThat(bookings).extracting(BookingEntity::getId).containsExactly(bookingId);
        assertThat(jpa.findById(bookingId)).isPresent();
    }
}
