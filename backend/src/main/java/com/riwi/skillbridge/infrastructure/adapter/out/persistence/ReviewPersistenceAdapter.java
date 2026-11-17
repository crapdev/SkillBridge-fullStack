package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.ReviewRepositoryPort;
import com.riwi.skillbridge.domain.exception.ReviewAlreadyExistsException;
import com.riwi.skillbridge.domain.model.PublicReview;
import com.riwi.skillbridge.domain.model.RatingSummary;
import com.riwi.skillbridge.domain.model.Review;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ReviewEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaReviewRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaReviewRepository.RecentRow;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaReviewRepository.SummaryRow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** HU-27: calificaciones en PostgreSQL (tabla reviews, migración V20). */
@Component
public class ReviewPersistenceAdapter implements ReviewRepositoryPort {

    private final JpaReviewRepository repository;

    public ReviewPersistenceAdapter(JpaReviewRepository repository) {
        this.repository = repository;
    }

    @Override
    public Review save(Review review) {
        try {
            return toDomain(repository.saveAndFlush(toEntity(review)));
        } catch (DataIntegrityViolationException duplicated) {
            // Otra petición calificó la misma reserva al mismo tiempo: la restricción UNIQUE dejó pasar solo una
            throw new ReviewAlreadyExistsException();
        }
    }

    @Override
    public boolean existsByBookingId(UUID bookingId) {
        return repository.existsByBookingId(bookingId);
    }

    @Override
    public List<Review> findByCustomerId(UUID customerId) {
        return repository.findByCustomerId(customerId).stream().map(this::toDomain).toList();
    }

    @Override
    public Map<UUID, RatingSummary> summarizeByOfferingIds(Collection<UUID> offeringIds) {
        if (offeringIds.isEmpty()) return Map.of();
        return repository.summarizeByOfferingIds(offeringIds).stream()
            .collect(Collectors.toMap(SummaryRow::getOfferingId, ReviewPersistenceAdapter::toSummary));
    }

    @Override
    public RatingSummary summarizeMentor(UUID providerId) {
        SummaryRow row = repository.summarizeMentor(providerId);
        return row == null ? RatingSummary.EMPTY : toSummary(row);
    }

    @Override
    public List<PublicReview> findLatestCommentedByOfferingId(UUID offeringId, int limit) {
        return repository.findLatestCommentedByOfferingId(offeringId, limit).stream()
            .map(ReviewPersistenceAdapter::toPublic)
            .toList();
    }

    private static RatingSummary toSummary(SummaryRow row) {
        long count = row.getTotal() == null ? 0 : row.getTotal().longValue();
        Double average = row.getAverage() == null ? null : row.getAverage().doubleValue();
        return new RatingSummary(average, count);
    }

    private static PublicReview toPublic(RecentRow row) {
        return new PublicReview(row.getRating().intValue(), row.getComment(),
            row.getMentorRating().intValue(), row.getMentorComment(),
            PublicReview.firstNameOf(row.getAuthorName()), toInstant(row.getCreatedAt()));
    }

    // Según la versión del driver, una columna TIMESTAMPTZ de una consulta nativa llega con distintos tipos
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Fecha de calificación con un tipo inesperado: " + value);
    }

    private ReviewEntity toEntity(Review review) {
        ReviewEntity entity = new ReviewEntity();
        entity.setId(review.id());
        entity.setBookingId(review.bookingId());
        entity.setRating(review.rating());
        entity.setComment(review.comment());
        entity.setMentorRating(review.mentorRating());
        entity.setMentorComment(review.mentorComment());
        entity.setCreatedAt(review.createdAt());
        return entity;
    }

    private Review toDomain(ReviewEntity entity) {
        return new Review(entity.getId(), entity.getBookingId(), entity.getRating(), entity.getComment(),
            entity.getMentorRating(), entity.getMentorComment(), entity.getCreatedAt());
    }
}
