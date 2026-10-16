package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-17 / HU-25: una mentoría solo es visible (catálogo, detalle y asistente de IA) si su proveedor
 * está aprobado y activo, además de estar activa y tener horarios futuros libres.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OfferingPersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VisibleOfferingsPersistenceTest {

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

    @Autowired OfferingPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    private final UUID providerId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void createOfferingWithFreeSlot() {
        jdbc.update("INSERT INTO app_users (id, name, email, password, role, status) VALUES (?, ?, ?, ?, 'PROVIDER', 'ACTIVE')",
                providerId, "Proveedor", providerId + "@test.com", "hash");
        jdbc.update("INSERT INTO offerings (id, provider_id, title, description, category, price, active) VALUES (?, ?, ?, ?, ?, ?, true)",
                offeringId, providerId, "Mentoría Kotlin", "Corrutinas", "BACKEND", 50000);
        jdbc.update("INSERT INTO availability_slots (id, offering_id, scheduled_at, reserved) VALUES (?, ?, ?, false)",
                UUID.randomUUID(), offeringId, Timestamp.from(Instant.now().plus(2, ChronoUnit.DAYS)));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM availability_slots WHERE offering_id = ?", offeringId);
        jdbc.update("DELETE FROM offerings WHERE id = ?", offeringId);
        jdbc.update("DELETE FROM app_users WHERE id = ?", providerId);
    }

    private boolean isVisible() {
        boolean inList = adapter.findVisible().stream().map(Offering::id).anyMatch(offeringId::equals);
        boolean inPage = adapter.findVisible(PageRequest.of(0, 100)).stream().map(Offering::id).anyMatch(offeringId::equals);
        boolean byId = adapter.findVisibleById(offeringId).isPresent();
        // Las tres consultas deben coincidir para que catálogo, detalle e IA muestren lo mismo
        assertThat(inPage).isEqualTo(inList);
        assertThat(byId).isEqualTo(inList);
        return inList;
    }

    @Test
    void offeringOfActiveProviderIsVisible() {
        assertThat(isVisible()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "PENDING_APPROVAL", "REJECTED"})
    void offeringOfNotApprovedProviderIsHidden(String status) {
        jdbc.update("UPDATE app_users SET status = ? WHERE id = ?", status, providerId);

        assertThat(isVisible()).isFalse();
    }

    @Test
    void offeringIsVisibleAgainWhenProviderIsReactivated() {
        jdbc.update("UPDATE app_users SET status = 'INACTIVE' WHERE id = ?", providerId);
        jdbc.update("UPDATE app_users SET status = 'ACTIVE' WHERE id = ?", providerId);

        assertThat(isVisible()).isTrue();
    }

    // HU-21: el criterio de visibilidad incluye que un administrador haya aprobado la mentoría
    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "REJECTED"})
    void offeringNotApprovedByAdminIsHidden(String reviewStatus) {
        jdbc.update("UPDATE offerings SET review_status = ? WHERE id = ?", reviewStatus, offeringId);

        assertThat(isVisible()).isFalse();
    }

    @Test
    void offeringBecomesVisibleOnceApproved() {
        jdbc.update("UPDATE offerings SET review_status = 'PENDING_REVIEW' WHERE id = ?", offeringId);
        jdbc.update("UPDATE offerings SET review_status = 'APPROVED' WHERE id = ?", offeringId);

        assertThat(isVisible()).isTrue();
    }

    @Test
    void pendingQueueListsOnlyPendingOfferings() {
        jdbc.update("UPDATE offerings SET review_status = 'PENDING_REVIEW' WHERE id = ?", offeringId);

        assertThat(adapter.findByReviewStatus(com.riwi.skillbridge.domain.model.OfferingReviewStatus.PENDING_REVIEW))
                .extracting(Offering::id).containsExactly(offeringId);
    }
}
