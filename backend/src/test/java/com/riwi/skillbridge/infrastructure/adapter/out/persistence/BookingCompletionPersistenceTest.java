package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** BUG-05: el cambio a COMPLETED en la base es atómico y solo encuentra confirmadas que ya empezaron. */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingCompletionPersistenceTest {

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

    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired JpaBookingRepository jpa;
    @Autowired JdbcTemplate jdbc;

    private BookingPersistenceAdapter adapter;
    private UUID customerId;
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        adapter = new BookingPersistenceAdapter(jpa);
        // bookings.customer_id tiene clave foránea a app_users
        customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            customerId, "Tester", customerId + "@test.com", "hash-no-importa", "CUSTOMER");
    }

    private Booking saved(Instant scheduledAt, BookingStatus status) {
        return adapter.save(new Booking(UUID.randomUUID(), OFFERING_ID, customerId, scheduledAt, status));
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, id);
    }

    @Test
    void findsOnlyConfirmedBookingsThatAlreadyStarted() {
        Booking confirmedPast = saved(now.minus(2, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
        saved(now.plus(1, ChronoUnit.DAYS), BookingStatus.CONFIRMED);
        saved(now.minus(2, ChronoUnit.HOURS).plusSeconds(1), BookingStatus.CREATED);
        saved(now.minus(2, ChronoUnit.HOURS).plusSeconds(2), BookingStatus.CANCELLED);

        assertThat(adapter.findConfirmedScheduledBefore(now))
            .extracting(Booking::id)
            .containsExactly(confirmedPast.id());
    }

    @Test
    void updateStatusIf_changesOnlyOnce() {
        Booking confirmed = saved(now.minus(2, ChronoUnit.HOURS), BookingStatus.CONFIRMED);

        boolean first = adapter.updateStatusIf(confirmed.id(), BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
        boolean second = adapter.updateStatusIf(confirmed.id(), BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

        assertThat(first).isTrue();
        assertThat(second).as("el segundo proceso no debe volver a publicar el evento").isFalse();
        assertThat(statusOf(confirmed.id())).isEqualTo("COMPLETED");
    }

    @Test
    void updateStatusIf_doesNotTouchABookingInAnotherState() {
        Booking cancelled = saved(now.minus(2, ChronoUnit.HOURS), BookingStatus.CANCELLED);

        assertThat(adapter.updateStatusIf(cancelled.id(), BookingStatus.CONFIRMED, BookingStatus.COMPLETED)).isFalse();
        assertThat(statusOf(cancelled.id())).isEqualTo("CANCELLED");
    }
}
