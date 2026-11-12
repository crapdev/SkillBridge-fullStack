package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** SCRUM-207 (HU-27): reglas de una calificación y de su resumen. */
class ReviewTest {

    private final UUID bookingId = UUID.randomUUID();

    @Test
    void createsAReviewWithBothRatings() {
        Review review = Review.create(bookingId, 5, "Muy clara", 4, "Puntual", Instant.now());

        assertNotNull(review.id());
        assertEquals(bookingId, review.bookingId());
        assertEquals(5, review.rating());
        assertEquals(4, review.mentorRating());
        assertEquals("Muy clara", review.comment());
        assertEquals("Puntual", review.mentorComment());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void sessionRatingMustBeFrom1To5(int stars) {
        assertThrows(BusinessRuleException.class, () -> Review.create(bookingId, stars, null, 3, null, Instant.now()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void mentorRatingMustBeFrom1To5(int stars) {
        assertThrows(BusinessRuleException.class, () -> Review.create(bookingId, 3, null, stars, null, Instant.now()));
    }

    @Test
    void commentsAreOptionalAndBlankMeansNoComment() {
        Review review = Review.create(bookingId, 3, "   ", 3, null, Instant.now());

        assertNull(review.comment());
        assertNull(review.mentorComment());
    }

    @Test
    void commentsAreTrimmed() {
        assertEquals("Excelente", Review.create(bookingId, 5, "  Excelente  ", 5, null, Instant.now()).comment());
    }

    @Test
    void commentLongerThan500CharactersIsRejected() {
        String tooLong = "a".repeat(Review.MAX_COMMENT + 1);

        assertThrows(BusinessRuleException.class, () -> Review.create(bookingId, 4, tooLong, 4, null, Instant.now()));
        assertThrows(BusinessRuleException.class, () -> Review.create(bookingId, 4, null, 4, tooLong, Instant.now()));
        assertDoesNotThrow(() -> Review.create(bookingId, 4, "a".repeat(Review.MAX_COMMENT), 4, null, Instant.now()));
    }

    @Test
    void summaryIsRoundedToOneDecimal() {
        assertEquals(4.7, new RatingSummary(4.6666, 3).average());
        assertEquals(3, new RatingSummary(4.6666, 3).count());
    }

    @Test
    void summaryWithoutReviewsHasNoAverage() {
        // "Sin calificaciones" no es lo mismo que "0 estrellas"
        assertNull(new RatingSummary(null, 0).average());
        assertNull(new RatingSummary(3.0, 0).average());
        assertEquals(RatingSummary.EMPTY, new RatingSummary(null, 0));
    }

    @Test
    void publicReviewShowsOnlyTheFirstName() {
        assertEquals("Laura", PublicReview.firstNameOf("Laura Gómez Ruiz"));
        assertEquals("Ana", PublicReview.firstNameOf("  Ana  "));
        assertEquals("Cliente", PublicReview.firstNameOf(null));
        assertEquals("Cliente", PublicReview.firstNameOf(" "));
    }

    @Test
    void onlyACompletedBookingCountsAsCompleted() {
        for (BookingStatus status : BookingStatus.values()) {
            Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), status,
                    java.math.BigDecimal.TEN);
            assertEquals(status == BookingStatus.COMPLETED, booking.isCompleted(), status.name());
        }
    }
}
