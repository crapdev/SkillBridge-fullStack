package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.SyllabusTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-175 (HU-20): migración V8 y persistencia de la ficha contra PostgreSQL real.
 * Sin transacción de prueba: cada lectura va a la base y no a la caché de Hibernate.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OfferingPersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OfferingProfilePersistenceTest {

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

    private static final UUID JAVA_SEED = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired OfferingPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    private UUID createdId;

    @AfterEach
    void cleanUp() {
        if (createdId != null) {
            jdbc.update("DELETE FROM availability_slots WHERE offering_id = ?", createdId);
            adapter.deleteById(createdId);
        }
    }

    private UUID anyUserId() {
        return jdbc.queryForObject("SELECT id FROM app_users LIMIT 1", UUID.class);
    }

    private Offering offering(UUID id, UUID providerId, List<SyllabusTopic> topics, int duration, OfferingLevel level,
                              EnumSet<DayOfWeek> days) {
        return new Offering(id, providerId, "Kubernetes", "Primeros pasos", "CLOUD", new BigDecimal("90000"), true,
                topics, duration, level, days);
    }

    // ==========================================
    // MIGRACIÓN V8: VALORES POR DEFECTO VÁLIDOS
    // ==========================================

    @Test
    void migration_ShouldGiveExistingOfferingsValidDefaults() {
        List<Offering> all = adapter.findAllActive();

        assertThat(all).isNotEmpty().allSatisfy(o -> {
            assertThat(o.syllabus()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(o.durationMinutes()).isEqualTo(60);
            assertThat(o.workingDays()).containsExactlyElementsOf(EnumSet.allOf(DayOfWeek.class));
        });
    }

    @Test
    void migration_ShouldMoveFixedFrontendContentToSeedOfferings() {
        Offering java = adapter.findById(JAVA_SEED).orElseThrow();

        assertThat(java.level()).isEqualTo(OfferingLevel.INTERMEDIO);
        assertThat(java.syllabus()).extracting(SyllabusTopic::title)
                .containsExactly("Arquitectura hexagonal", "APIs REST", "Seguridad", "Persistencia");
    }

    // ==========================================
    // GUARDAR, EDITAR Y BORRAR LA FICHA
    // ==========================================

    @Test
    void save_ShouldPersistProfileKeepingTopicOrderAndDays() {
        createdId = UUID.randomUUID();
        adapter.save(offering(createdId, anyUserId(),
                List.of(new SyllabusTopic("Pods", "Qué son"), new SyllabusTopic("Deployments", "Réplicas"),
                        new SyllabusTopic("Services", "Exponer pods")),
                90, OfferingLevel.AVANZADO, EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)));

        Offering read = adapter.findById(createdId).orElseThrow();

        assertThat(read.syllabus()).extracting(SyllabusTopic::title).containsExactly("Pods", "Deployments", "Services");
        assertThat(read.durationMinutes()).isEqualTo(90);
        assertThat(read.level()).isEqualTo(OfferingLevel.AVANZADO);
        assertThat(read.workingDays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void update_ShouldReplaceProfileAndKeepCreationDate() {
        createdId = UUID.randomUUID();
        UUID provider = anyUserId();
        adapter.save(offering(createdId, provider,
                List.of(new SyllabusTopic("A", "a"), new SyllabusTopic("B", "b"), new SyllabusTopic("C", "c")),
                60, OfferingLevel.BASICO, EnumSet.allOf(DayOfWeek.class)));
        Timestamp createdAt = jdbc.queryForObject("SELECT created_at FROM offerings WHERE id = ?", Timestamp.class, createdId);

        adapter.update(offering(createdId, provider, List.of(new SyllabusTopic("Z", "z"), new SyllabusTopic("A", "a")),
                30, OfferingLevel.INTERMEDIO, EnumSet.of(DayOfWeek.SUNDAY)));

        Offering read = adapter.findById(createdId).orElseThrow();
        assertThat(read.syllabus()).extracting(SyllabusTopic::title).containsExactly("Z", "A");
        assertThat(read.workingDays()).containsExactly(DayOfWeek.SUNDAY);
        assertThat(read.durationMinutes()).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offering_topics WHERE offering_id = ?", Integer.class, createdId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT created_at FROM offerings WHERE id = ?", Timestamp.class, createdId))
                .isEqualTo(createdAt);
    }

    @Test
    void delete_ShouldRemoveTopicsAndDays() {
        UUID id = UUID.randomUUID();
        adapter.save(offering(id, anyUserId(), List.of(new SyllabusTopic("A", "a"), new SyllabusTopic("B", "b")),
                60, OfferingLevel.BASICO, EnumSet.of(DayOfWeek.MONDAY)));

        adapter.deleteById(id);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM offering_topics WHERE offering_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offering_working_days WHERE offering_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void visibleCatalogPage_ShouldIncludeProfile() {
        createdId = UUID.randomUUID();
        adapter.save(offering(createdId, anyUserId(), List.of(new SyllabusTopic("A", "a"), new SyllabusTopic("B", "b")),
                120, OfferingLevel.AVANZADO, EnumSet.of(DayOfWeek.TUESDAY)));
        jdbc.update("INSERT INTO availability_slots (id, offering_id, scheduled_at, reserved) VALUES (?, ?, ?, false)",
                UUID.randomUUID(), createdId, Timestamp.from(Instant.now().plus(3, ChronoUnit.DAYS)));

        Offering visible = adapter.findVisible(PageRequest.of(0, 50)).getContent().stream()
                .filter(o -> o.id().equals(createdId)).findFirst().orElseThrow();

        assertThat(visible.syllabus()).hasSize(2);
        assertThat(visible.durationMinutes()).isEqualTo(120);
        assertThat(visible.workingDays()).containsExactly(DayOfWeek.TUESDAY);
    }
}
