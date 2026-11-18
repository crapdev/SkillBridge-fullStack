package com.riwi.skillbridge.infrastructure.adapter.out.analytics;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresAnalyticsReaderTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void readsCurrentBookingsAndRecalculatesAfterCancellation() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var reader = new PostgresAnalyticsReader(jdbc);
        UUID customer = UUID.randomUUID();
        UUID booking = UUID.randomUUID();

        assertThat(reader.read()).containsEntry("reservasPorEstado", List.of());
        jdbc.update("""
                INSERT INTO app_users (id, name, email, password, role)
                VALUES (?, 'Cliente de prueba', 'analytics@example.test', 'hash', 'CUSTOMER')
                """, customer);
        jdbc.update("""
                INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status, price)
                VALUES (?, '11111111-1111-1111-1111-111111111111', ?, now(), 'CONFIRMED', 85000)
                """, booking, customer);
        assertThat(reader.read()).containsEntry("reservasPorEstado",
                List.of(Map.of("status", "CONFIRMED", "reservas", 1L)));

        jdbc.update("UPDATE bookings SET status = 'CANCELLED' WHERE id = ?", booking);
        Map<String, Object> payload = reader.read();
        assertThat(payload).containsEntry("source", "PostgreSQL")
                .containsEntry("reservasPorEstado", List.of(Map.of("status", "CANCELLED", "reservas", 1L)));
        assertThat(payload.get("tasaCancelacion")).asList().singleElement().satisfies(row ->
                assertThat(row).isInstanceOfSatisfying(Map.class, fields -> {
                    assertThat(fields.get("canceladas")).isEqualTo(1L);
                    assertThat(fields.get("tasa_cancelacion")).isEqualTo(new BigDecimal("100.00"));
                }));
        assertThat(payload.get("completitud")).asList().hasSize(6);
    }
}
