package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Notification;
import com.riwi.skillbridge.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba la migración V7 y el adaptador contra PostgreSQL real, incluida la restricción UNIQUE. */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationPersistenceAdapter.class)
class NotificationPersistenceAdapterTest {

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

    // Mentoría sembrada por V1__init.sql
    private static final UUID JAVA_OFFERING = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    NotificationPersistenceAdapter adapter;

    @Autowired
    JdbcTemplate jdbc;

    private UUID customerId;
    private UUID otherUserId;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        customerId = insertUser("cliente");
        otherUserId = insertUser("otro");
        bookingId = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status) VALUES (?, ?, ?, NOW() + INTERVAL '2 days', 'CREATED')",
                bookingId, JAVA_OFFERING, customerId);
    }

    @Test
    void sameEventTwiceIsSavedOnlyOnce() {
        assertThat(adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_CREATED, Instant.now()))).isTrue();
        assertThat(adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_CREATED, Instant.now()))).isFalse();

        assertThat(adapter.findLatestByRecipient(customerId, 10)).hasSize(1);
    }

    @Test
    void sameBookingWithAnotherTypeOrRecipientIsSaved() {
        adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_CREATED, Instant.now()));

        assertThat(adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_PAID, Instant.now()))).isTrue();
        assertThat(adapter.saveIfAbsent(notification(otherUserId, NotificationType.BOOKING_CREATED, Instant.now()))).isTrue();
    }

    @Test
    void listsOnlyTheRecipientNewestFirstWithLimit() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_CREATED, now.minusSeconds(60)));
        adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_PAID, now));
        adapter.saveIfAbsent(notification(otherUserId, NotificationType.BOOKING_CREATED, now));

        assertThat(adapter.findLatestByRecipient(customerId, 10))
                .extracting(Notification::type)
                .containsExactly(NotificationType.BOOKING_PAID, NotificationType.BOOKING_CREATED);
        assertThat(adapter.findLatestByRecipient(customerId, 1)).hasSize(1);
    }

    @Test
    void markAllReadOnlyAffectsTheRecipient() {
        adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_CREATED, Instant.now()));
        adapter.saveIfAbsent(notification(customerId, NotificationType.BOOKING_PAID, Instant.now()));
        adapter.saveIfAbsent(notification(otherUserId, NotificationType.BOOKING_CREATED, Instant.now()));
        assertThat(adapter.countUnread(customerId)).isEqualTo(2);

        adapter.markAllRead(customerId, Instant.now());

        assertThat(adapter.countUnread(customerId)).isZero();
        assertThat(adapter.countUnread(otherUserId)).isEqualTo(1);
        assertThat(adapter.findLatestByRecipient(customerId, 10)).allMatch(Notification::isRead);
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, 'hash', 'CUSTOMER')",
                id, name, name + "-" + id + "@test.com");
        return id;
    }

    private Notification notification(UUID recipient, NotificationType type, Instant createdAt) {
        return new Notification(UUID.randomUUID(), recipient, bookingId, type, "Mensaje de prueba", createdAt, null);
    }
}
