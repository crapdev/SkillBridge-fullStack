package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase.OfferingRating;
import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase.OfferingReviews;
import com.riwi.skillbridge.application.port.in.RateBookingUseCase.ReviewDraft;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.ReviewRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ReviewAlreadyExistsException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PublicReview;
import com.riwi.skillbridge.domain.model.RatingSummary;
import com.riwi.skillbridge.domain.model.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** SCRUM-208 (HU-27): calificar reservas y consultar promedios. */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepositoryPort reviews;
    @Mock private BookingRepositoryPort bookings;
    @Mock private OfferingRepositoryPort offerings;
    @Mock private UserAccountPort users;
    @Mock private CompleteFinishedBookingsUseCase completion;

    private final Instant now = Instant.parse("2026-11-11T15:00:00Z");
    private ReviewService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();
    private final ReviewDraft draft = new ReviewDraft(5, "Muy clara", 4, "Puntual");

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviews, bookings, offerings, users, completion, Clock.fixed(now, ZoneOffset.UTC));
        lenient().when(users.findIdByEmail("ana@test.com")).thenReturn(Optional.of(customerId));
        // BUG-05: el completado tiene sus propias pruebas; aquí deja las reservas como vienen
        lenient().when(completion.completeIfFinished(anyList())).thenAnswer(i -> i.getArgument(0));
        lenient().when(reviews.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Booking booking(UUID owner, BookingStatus status) {
        Booking booking = new Booking(UUID.randomUUID(), offeringId, owner, now.minusSeconds(7200), status,
                new BigDecimal("85000"));
        lenient().when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        return booking;
    }

    // ---- Calificar ----

    @Test
    void customerRatesOwnCompletedBooking() {
        Booking booking = booking(customerId, BookingStatus.COMPLETED);

        Review review = service.rate(booking.id(), "ana@test.com", draft);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviews).save(saved.capture());
        assertEquals(booking.id(), saved.getValue().bookingId());
        assertEquals(5, review.rating());
        assertEquals("Muy clara", review.comment());
        assertEquals(4, review.mentorRating());
        assertEquals("Puntual", review.mentorComment());
        assertEquals(now, review.createdAt());
    }

    @Test
    void cannotRateTheBookingOfAnotherCustomer() {
        Booking booking = booking(UUID.randomUUID(), BookingStatus.COMPLETED);

        assertThrows(UnauthorizedActionException.class, () -> service.rate(booking.id(), "ana@test.com", draft));
        verify(reviews, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CREATED", "CONFIRMED", "CANCELLED"})
    void onlyCompletedBookingsCanBeRated(BookingStatus status) {
        Booking booking = booking(customerId, status);

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.rate(booking.id(), "ana@test.com", draft));
        assertTrue(error.getMessage().contains("completar la sesión"));
        verify(reviews, never()).save(any());
    }

    @Test
    void aFinishedSessionNotYetMarkedCompletedCanBeRated() {
        // BUG-05: el proceso programado aún no la marcó, pero la sesión ya terminó
        Booking confirmed = booking(customerId, BookingStatus.CONFIRMED);
        Booking completed = new Booking(confirmed.id(), offeringId, customerId, confirmed.scheduledAt(),
                BookingStatus.COMPLETED, confirmed.price());
        when(completion.completeIfFinished(List.of(confirmed))).thenReturn(List.of(completed));

        assertDoesNotThrow(() -> service.rate(confirmed.id(), "ana@test.com", draft));
        verify(reviews).save(any());
    }

    @Test
    void aBookingIsRatedOnlyOnce() {
        Booking booking = booking(customerId, BookingStatus.COMPLETED);
        when(reviews.existsByBookingId(booking.id())).thenReturn(true);

        assertThrows(ReviewAlreadyExistsException.class, () -> service.rate(booking.id(), "ana@test.com", draft));
        verify(reviews, never()).save(any());
    }

    @Test
    void invalidStarsAreRejected() {
        Booking booking = booking(customerId, BookingStatus.COMPLETED);

        assertThrows(BusinessRuleException.class,
                () -> service.rate(booking.id(), "ana@test.com", new ReviewDraft(6, null, 3, null)));
        verify(reviews, never()).save(any());
    }

    @Test
    void unknownBookingFails() {
        UUID missing = UUID.randomUUID();
        when(bookings.findById(missing)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.rate(missing, "ana@test.com", draft));
    }

    @Test
    void listsOnlyTheReviewsOfTheAuthenticatedCustomer() {
        Review mine = Review.create(UUID.randomUUID(), 4, null, 4, null, now);
        when(reviews.findByCustomerId(customerId)).thenReturn(List.of(mine));

        assertEquals(List.of(mine), service.myReviews("ana@test.com"));
    }

    // ---- Catálogo y detalle ----

    @Test
    void catalogGetsOneEntryPerOfferingIncludingTheOnesWithoutReviews() {
        UUID rated = UUID.randomUUID();
        UUID notRated = UUID.randomUUID();
        when(reviews.summarizeByOfferingIds(List.of(rated, notRated)))
                .thenReturn(Map.of(rated, new RatingSummary(4.5, 2)));

        List<OfferingRating> result = service.ratingsOf(List.of(rated, notRated, rated));

        assertEquals(List.of(new OfferingRating(rated, 4.5, 2), new OfferingRating(notRated, null, 0)), result);
    }

    @Test
    void catalogWithoutIdsDoesNotQuery() {
        assertEquals(List.of(), service.ratingsOf(Collections.emptyList()));
        verifyNoInteractions(reviews);
    }

    @Test
    void catalogRejectsTooManyIds() {
        List<UUID> tooMany = IntStream.rangeClosed(0, ReviewService.MAX_IDS_PER_REQUEST).mapToObj(i -> UUID.randomUUID()).toList();

        assertThrows(BusinessRuleException.class, () -> service.ratingsOf(tooMany));
    }

    @Test
    void detailShowsOfferingAverageMentorAverageAndRecentComments() {
        Offering offering = new Offering(offeringId, providerId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findVisibleById(offeringId)).thenReturn(Optional.of(offering));
        when(reviews.summarizeByOfferingIds(List.of(offeringId))).thenReturn(Map.of(offeringId, new RatingSummary(4.0, 3)));
        when(reviews.summarizeMentor(providerId)).thenReturn(new RatingSummary(4.8, 10));
        List<PublicReview> recent = List.of(new PublicReview(5, "Excelente", 5, null, "Ana", now));
        when(reviews.findLatestCommentedByOfferingId(offeringId, ReviewService.RECENT_REVIEWS)).thenReturn(recent);

        OfferingReviews result = service.reviewsOf(offeringId);

        assertEquals(new RatingSummary(4.0, 3), result.offering());
        assertEquals(new RatingSummary(4.8, 10), result.mentor());
        assertEquals(recent, result.recent());
    }

    @Test
    void detailOfAnOfferingWithoutReviewsHasNoAverage() {
        Offering offering = new Offering(offeringId, providerId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findVisibleById(offeringId)).thenReturn(Optional.of(offering));
        when(reviews.summarizeByOfferingIds(List.of(offeringId))).thenReturn(Map.of());
        when(reviews.summarizeMentor(providerId)).thenReturn(RatingSummary.EMPTY);

        OfferingReviews result = service.reviewsOf(offeringId);

        assertEquals(RatingSummary.EMPTY, result.offering());
        assertTrue(result.recent().isEmpty());
    }

    @Test
    void detailOfAnOfferingThePublicCannotSeeFails() {
        when(offerings.findVisibleById(offeringId)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.reviewsOf(offeringId));
    }
}
