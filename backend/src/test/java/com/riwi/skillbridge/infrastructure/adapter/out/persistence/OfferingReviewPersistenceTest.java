package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-178 (HU-21): migración V9 y persistencia del estado de revisión contra PostgreSQL real.
 * Sin transacción de prueba: cada lectura va a la base y no a la caché de Hibernate.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OfferingPersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OfferingReviewPersistenceTest {

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

    // Las tres mentorías semilla de V1: existían antes de V9
    private static final List<UUID> SEED_IDS = List.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            UUID.fromString("33333333-3333-3333-3333-333333333333"));

    @Autowired OfferingPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    private UUID createdId;

    @AfterEach
    void cleanUp() {
        if (createdId != null) adapter.deleteById(createdId);
    }

    private Offering newPendingOffering() {
        createdId = UUID.randomUUID();
        UUID providerId = jdbc.queryForObject("SELECT id FROM app_users LIMIT 1", UUID.class);
        return new Offering(createdId, providerId, "Kubernetes", "Primeros pasos", "CLOUD", new BigDecimal("90000"), true)
                .pendingReview();
    }

    @Test
    void migration_existingOfferingsStayApproved() {
        assertThat(SEED_IDS).allSatisfy(id ->
                assertThat(adapter.findById(id).orElseThrow().reviewStatus()).isEqualTo(OfferingReviewStatus.APPROVED));
    }

    @Test
    void save_keepsPendingReview() {
        adapter.save(newPendingOffering());

        assertThat(adapter.findById(createdId).orElseThrow().reviewStatus()).isEqualTo(OfferingReviewStatus.PENDING_REVIEW);
    }

    @Test
    void update_storesTheRejectionAndItsReason() {
        Offering pending = adapter.save(newPendingOffering());

        adapter.update(pending.reject("El temario no corresponde a la categoría"));

        Offering stored = adapter.findById(createdId).orElseThrow();
        assertThat(stored.reviewStatus()).isEqualTo(OfferingReviewStatus.REJECTED);
        assertThat(stored.rejectionReason()).isEqualTo("El temario no corresponde a la categoría");
    }

    @Test
    void database_rejectsUnknownStatus() {
        adapter.save(newPendingOffering());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                jdbc.update("UPDATE offerings SET review_status = 'ARCHIVADA' WHERE id = ?", createdId));
    }
}
