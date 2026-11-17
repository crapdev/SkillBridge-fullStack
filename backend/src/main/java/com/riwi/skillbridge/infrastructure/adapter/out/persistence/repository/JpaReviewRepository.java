package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * HU-27: la calificación solo guarda booking_id; la mentoría, el cliente y el mentor se obtienen uniendo
 * reviews -> bookings -> offerings / app_users. Los alias van entre comillas porque PostgreSQL pasa a minúsculas
 * los que no las tienen y la proyección busca el nombre exacto de cada getter.
 */
public interface JpaReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    boolean existsByBookingId(UUID bookingId);

    /** Fila de un promedio: id de la mentoría (o null para el mentor), promedio y cantidad. */
    interface SummaryRow {
        UUID getOfferingId();
        Number getAverage();
        Number getTotal();
    }

    /** Fila de una calificación reciente con el nombre de quien la dejó. */
    interface RecentRow {
        Number getRating();
        String getComment();
        Number getMentorRating();
        String getMentorComment();
        String getAuthorName();
        Object getCreatedAt();
    }

    @Query(value = """
            SELECT r.* FROM reviews r
            JOIN bookings b ON b.id = r.booking_id
            WHERE b.customer_id = :customerId
            ORDER BY r.created_at DESC
            """, nativeQuery = true)
    List<ReviewEntity> findByCustomerId(@Param("customerId") UUID customerId);

    @Query(value = """
            SELECT b.offering_id AS "offeringId", AVG(r.rating) AS "average", COUNT(r.id) AS "total"
            FROM reviews r
            JOIN bookings b ON b.id = r.booking_id
            WHERE b.offering_id IN (:offeringIds)
            GROUP BY b.offering_id
            """, nativeQuery = true)
    List<SummaryRow> summarizeByOfferingIds(@Param("offeringIds") Collection<UUID> offeringIds);

    // El mentor se califica en cada reserva; su promedio junta las calificaciones de todas sus mentorías
    @Query(value = """
            SELECT CAST(NULL AS UUID) AS "offeringId", AVG(r.mentor_rating) AS "average", COUNT(r.id) AS "total"
            FROM reviews r
            JOIN bookings b ON b.id = r.booking_id
            JOIN offerings o ON o.id = b.offering_id
            WHERE o.provider_id = :providerId
            """, nativeQuery = true)
    SummaryRow summarizeMentor(@Param("providerId") UUID providerId);

    @Query(value = """
            SELECT r.rating AS "rating", r.comment AS "comment",
                   r.mentor_rating AS "mentorRating", r.mentor_comment AS "mentorComment",
                   u.name AS "authorName", r.created_at AS "createdAt"
            FROM reviews r
            JOIN bookings b ON b.id = r.booking_id
            JOIN app_users u ON u.id = b.customer_id
            WHERE b.offering_id = :offeringId
              AND (r.comment IS NOT NULL OR r.mentor_comment IS NOT NULL)
            ORDER BY r.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RecentRow> findLatestCommentedByOfferingId(@Param("offeringId") UUID offeringId, @Param("limit") int limit);
}
