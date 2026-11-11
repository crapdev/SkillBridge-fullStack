package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.exception.ReviewAlreadyExistsException;
import com.riwi.skillbridge.domain.model.PublicReview;
import com.riwi.skillbridge.domain.model.RatingSummary;
import com.riwi.skillbridge.domain.model.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-207/211 (HU-27): migración V20 y adaptador de calificaciones contra PostgreSQL real. */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReviewPersistenceAdapter.class)
class ReviewPersistenceAdapterTest {

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

    @Autowired ReviewPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private UUID provider;
    private UUID otherProvider;
    private UUID java;      // mentoría del proveedor
    private UUID angular;   // otra mentoría del mismo proveedor
    private UUID docker;    // mentoría de otro proveedor
    private UUID ana;
    private UUID luis;

    @BeforeEach
    void setUp() {
        provider = insertUser("Laura Mentora", "PROVIDER");
        otherProvider = insertUser("Pedro Mentor", "PROVIDER");
        ana = insertUser("Ana María López", "CUSTOMER");
        luis = insertUser("Luis", "CUSTOMER");
        java = insertOffering(provider, "Java");
        angular = insertOffering(provider, "Angular");
        docker = insertOffering(otherProvider, "Docker");
    }

    @Test
    void savesAndReadsBackAReview() {
        UUID booking = insertCompletedBooking(java, ana);
        Review saved = adapter.save(Review.create(booking, 5, "Muy clara", 4, null, now));

        assertThat(adapter.existsByBookingId(booking)).isTrue();
        assertThat(adapter.findByCustomerId(ana)).containsExactly(saved);
        assertThat(adapter.findByCustomerId(luis)).isEmpty();
    }

    @Test
    void aBookingCanBeReviewedOnlyOnce() {
        UUID booking = insertCompletedBooking(java, ana);
        adapter.save(Review.create(booking, 5, null, 5, null, now));

        assertThatThrownBy(() -> adapter.save(Review.create(booking, 1, null, 1, null, now)))
                .isInstanceOf(ReviewAlreadyExistsException.class);
    }

    @Test
    void theDatabaseRejectsStarsOutOfRange() {
        UUID booking = insertCompletedBooking(java, ana);

        // La regla vive en el dominio, pero la base también la protege (como las restricciones de BUG-11)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO reviews (id, booking_id, rating, mentor_rating) VALUES (?, ?, 6, 3)", UUID.randomUUID(), booking))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void summarizesEachOfferingSeparately() {
        adapter.save(Review.create(insertCompletedBooking(java, ana), 5, null, 5, null, now));
        adapter.save(Review.create(insertCompletedBooking(java, luis), 4, null, 3, null, now));
        adapter.save(Review.create(insertCompletedBooking(angular, ana), 2, null, 4, null, now));

        Map<UUID, RatingSummary> summaries = adapter.summarizeByOfferingIds(List.of(java, angular, docker));

        assertThat(summaries.get(java)).isEqualTo(new RatingSummary(4.5, 2));
        assertThat(summaries.get(angular)).isEqualTo(new RatingSummary(2.0, 1));
        assertThat(summaries).doesNotContainKey(docker);   // sin calificaciones
    }

    @Test
    void mentorAverageJoinsAllTheirOfferingsAndUsesTheMentorRating() {
        adapter.save(Review.create(insertCompletedBooking(java, ana), 1, null, 5, null, now));
        adapter.save(Review.create(insertCompletedBooking(angular, luis), 1, null, 4, null, now));
        adapter.save(Review.create(insertCompletedBooking(docker, ana), 5, null, 1, null, now));   // de otro mentor

        assertThat(adapter.summarizeMentor(provider)).isEqualTo(new RatingSummary(4.5, 2));
        assertThat(adapter.summarizeMentor(UUID.randomUUID())).isEqualTo(RatingSummary.EMPTY);
    }

    @Test
    void latestCommentedReviewsAreNewestFirstWithFirstNameAndLimit() {
        adapter.save(Review.create(insertCompletedBooking(java, ana), 5, "La mejor", 5, null, now.minusSeconds(60)));
        adapter.save(Review.create(insertCompletedBooking(java, luis), 4, null, 4, "Muy paciente", now));
        adapter.save(Review.create(insertCompletedBooking(java, ana), 3, null, 3, null, now.plusSeconds(60)));  // sin comentario
        adapter.save(Review.create(insertCompletedBooking(angular, ana), 5, "Otra mentoría", 5, null, now));

        List<PublicReview> recent = adapter.findLatestCommentedByOfferingId(java, 5);

        assertThat(recent).extracting(PublicReview::authorName).containsExactly("Luis", "Ana");
        assertThat(recent.get(0).mentorComment()).isEqualTo("Muy paciente");
        assertThat(recent.get(1).comment()).isEqualTo("La mejor");
        assertThat(recent.get(1).createdAt()).isEqualTo(now.minusSeconds(60));

        assertThat(adapter.findLatestCommentedByOfferingId(java, 1)).hasSize(1);
    }

    private UUID insertUser(String name, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, 'hash', ?)",
                id, name, id + "@test.com", role);
        return id;
    }

    private UUID insertOffering(UUID providerId, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO offerings (id, provider_id, title, description, category, price) VALUES (?, ?, ?, 'x', 'BACKEND', 85000)",
                id, providerId, title);
        return id;
    }

    // BUG-08: una mentoría no puede tener dos reservas activas a la misma hora; cada reserva va en su propia hora
    private int bookingsCreated = 0;

    private UUID insertCompletedBooking(UUID offeringId, UUID customerId) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(now.minus(2, ChronoUnit.DAYS).plus(++bookingsCreated, ChronoUnit.HOURS));
        jdbc.update("INSERT INTO bookings (id, offering_id, customer_id, scheduled_at, status, price) "
                + "VALUES (?, ?, ?, ?, 'COMPLETED', 85000)", id, offeringId, customerId, at);
        return id;
    }
}
