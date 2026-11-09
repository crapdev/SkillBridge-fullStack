package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

/** BUG-07: una reserva CANCELLED no debe bloquear el horario; las activas sí. */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingSlotAvailabilityPersistenceTest {

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

    private static final UUID OFFERING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired JpaBookingRepository jpa;
    @Autowired JdbcTemplate jdbc;

    private BookingPersistenceAdapter adapter;
    private UUID customerId;
    private final Instant scheduledAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        adapter = new BookingPersistenceAdapter(jpa);
        // bookings.customer_id tiene clave foránea a app_users
        customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            customerId, "Tester", customerId + "@test.com", "hash-no-importa", "CUSTOMER");
    }

    private void saved(BookingStatus status) {
        adapter.save(new Booking(UUID.randomUUID(), OFFERING_ID, customerId, scheduledAt, status));
    }

    @Test
    void cancelledBookingDoesNotBlockTheSlotForOtherCustomers() {
        saved(BookingStatus.CANCELLED);

        assertThat(adapter.existsActiveByOfferingIdAndScheduledAt(OFFERING_ID, scheduledAt))
            .as("una reserva cancelada no debe impedir que otro cliente reserve esa franja")
            .isFalse();
    }

    @Test
    void cancelledBookingDoesNotBlockTheCustomerAtThatTime() {
        saved(BookingStatus.CANCELLED);

        assertThat(adapter.existsActiveByCustomerIdAndScheduledAt(customerId, scheduledAt))
            .as("el cliente que canceló puede reservar otra mentoría a esa hora")
            .isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CREATED", "CONFIRMED", "COMPLETED"})
    void activeBookingStillBlocksTheSlotAndTheCustomer(BookingStatus status) {
        saved(status);

        assertThat(adapter.existsActiveByOfferingIdAndScheduledAt(OFFERING_ID, scheduledAt)).isTrue();
        assertThat(adapter.existsActiveByCustomerIdAndScheduledAt(customerId, scheduledAt)).isTrue();
    }

    @Test
    void activeBookingBlocksEvenIfAnEarlierOneAtThatTimeWasCancelled() {
        saved(BookingStatus.CANCELLED);
        saved(BookingStatus.CONFIRMED);

        assertThat(adapter.existsActiveByOfferingIdAndScheduledAt(OFFERING_ID, scheduledAt)).isTrue();
        assertThat(adapter.existsActiveByCustomerIdAndScheduledAt(customerId, scheduledAt)).isTrue();
    }
}
