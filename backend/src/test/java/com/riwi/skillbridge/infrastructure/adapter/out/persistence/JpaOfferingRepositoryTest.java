package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaOfferingRepositoryTest {

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

    @Autowired
    JpaOfferingRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private final List<UUID> createdOfferingIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : createdOfferingIds) {
            jdbc.update("DELETE FROM availability_slots WHERE offering_id = ?", id);
            repository.deleteById(id);
        }
        createdOfferingIds.clear();
    }

    private UUID activeUserId() {
        return jdbc.queryForObject("SELECT id FROM app_users WHERE status = 'ACTIVE' LIMIT 1", UUID.class);
    }

    @Test
    void flywayShouldSeedThreeActiveOfferings() {
        assertThat(repository.findByActiveTrueOrderByTitleAsc()).hasSize(3);
    }

    @Test
    @DisplayName("findVisibleOfferings solo incluye mentorías con horarios futuros libres en availability_slots (BUG-02)")
    void shouldFilterOutOfferingsWithoutFutureFreeAvailabilitySlots() {
        Instant now = Instant.now();
        UUID providerId = activeUserId();

        UUID withFutureSlotId = createOffering(providerId, "Mentoría con horario futuro libre");
        UUID withReservedSlotId = createOffering(providerId, "Mentoría con horario reservado");
        UUID withPastSlotId = createOffering(providerId, "Mentoría con horario pasado");
        UUID withoutSlotsId = createOffering(providerId, "Mentoría sin horarios");

        // 1. Horario futuro libre -> debe aparecer
        jdbc.update("INSERT INTO availability_slots (id, offering_id, scheduled_at, reserved) VALUES (?, ?, ?, false)",
                UUID.randomUUID(), withFutureSlotId, Timestamp.from(now.plus(2, ChronoUnit.DAYS)));

        // 2. Horario futuro pero ya reservado -> no debe aparecer
        jdbc.update("INSERT INTO availability_slots (id, offering_id, scheduled_at, reserved) VALUES (?, ?, ?, true)",
                UUID.randomUUID(), withReservedSlotId, Timestamp.from(now.plus(2, ChronoUnit.DAYS)));

        // 3. Horario libre pero en el pasado -> no debe aparecer
        jdbc.update("INSERT INTO availability_slots (id, offering_id, scheduled_at, reserved) VALUES (?, ?, ?, false)",
                UUID.randomUUID(), withPastSlotId, Timestamp.from(now.minus(2, ChronoUnit.DAYS)));

        List<UUID> visibleIds = repository.findVisibleOfferings(now).stream()
                .map(OfferingEntity::getId)
                .toList();

        assertThat(visibleIds)
                .contains(withFutureSlotId)
                .doesNotContain(withReservedSlotId, withPastSlotId, withoutSlotsId);
    }

    private UUID createOffering(UUID providerId, String title) {
        UUID id = UUID.randomUUID();
        OfferingEntity entity = new OfferingEntity(
                id,
                providerId,
                title,
                "Descripción de prueba",
                "BACKEND",
                BigDecimal.TEN,
                true,
                List.of(),
                60,
                OfferingLevel.BASICO,
                EnumSet.allOf(DayOfWeek.class),
                OfferingReviewStatus.APPROVED,
                null
        );
        repository.save(entity);
        createdOfferingIds.add(id);
        return id;
    }
}
