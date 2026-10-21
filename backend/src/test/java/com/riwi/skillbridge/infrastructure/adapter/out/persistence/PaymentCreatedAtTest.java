package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Payment;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** BUG-04: la fecha del pago la decide el servicio (Clock), no la base de datos. */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentPersistenceAdapter.class)
class PaymentCreatedAtTest {

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

    @Autowired PaymentPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    // 19/10/2026 23:30 en Bogotá
    private static final Instant PAGO_NOCTURNO = Instant.parse("2026-10-20T04:30:00Z");

    @Test
    void savedPaymentKeepsTheDateDecidedByTheService() {
        UUID customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            customerId, "Tester", "tester-fecha@test.com", "hash", "CUSTOMER");
        UUID bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status) VALUES (?, ?, ?, ?, ?)",
            bookingId, OFFERING_ID, customerId, Timestamp.from(PAGO_NOCTURNO.plusSeconds(86400)), "CREATED");

        adapter.save(new Payment(null, bookingId, new BigDecimal("85000"), "COP",
            "pi_fecha", PaymentStatus.PENDING, PAGO_NOCTURNO));

        Payment saved = adapter.findByStripeIntentId("pi_fecha").orElseThrow();
        assertThat(saved.createdAt()).isEqualTo(PAGO_NOCTURNO);
    }
}
