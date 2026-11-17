package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase;
import com.riwi.skillbridge.application.port.in.RateBookingUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.ReviewRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ReviewAlreadyExistsException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.RatingSummary;
import com.riwi.skillbridge.domain.model.Review;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HU-27: calificar reservas completadas y consultar los promedios de mentorías y mentores. */
@Service
public class ReviewService implements RateBookingUseCase, GetOfferingRatingsUseCase {
    /** Comentarios recientes que muestra el detalle de una mentoría. */
    static final int RECENT_REVIEWS = 5;
    /** Mentorías por consulta del catálogo: evita que una sola petición pida miles de ids. */
    static final int MAX_IDS_PER_REQUEST = 100;

    private final ReviewRepositoryPort reviews;
    private final BookingRepositoryPort bookings;
    private final OfferingRepositoryPort offerings;
    private final UserAccountPort users;
    private final CompleteFinishedBookingsUseCase completion;
    private final Clock clock;

    public ReviewService(ReviewRepositoryPort reviews, BookingRepositoryPort bookings, OfferingRepositoryPort offerings,
                         UserAccountPort users, CompleteFinishedBookingsUseCase completion, Clock clock) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.offerings = offerings;
        this.users = users;
        this.completion = completion;
        this.clock = clock;
    }

    @Override
    public Review rate(UUID bookingId, String customerEmail, ReviewDraft draft) {
        UUID customerId = customerIdOf(customerEmail);
        Booking booking = bookings.findById(bookingId)
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        // Solo el cliente que hizo la reserva puede calificarla
        if (!booking.customerId().equals(customerId)) {
            throw new UnauthorizedActionException("No puedes calificar una reserva de otro cliente");
        }

        // BUG-05: si la sesión ya terminó pero el proceso programado aún no la marcó, se completa ahora
        Booking current = completion.completeIfFinished(List.of(booking)).get(0);
        if (!current.isCompleted()) {
            throw new BusinessRuleException("Solo puedes calificar una mentoría después de completar la sesión");
        }

        // Aviso claro en el caso normal; si dos peticiones llegan a la vez, la restricción UNIQUE deja pasar una
        if (reviews.existsByBookingId(bookingId)) {
            throw new ReviewAlreadyExistsException();
        }

        Review review = Review.create(bookingId, draft.rating(), draft.comment(),
            draft.mentorRating(), draft.mentorComment(), clock.instant());
        return reviews.save(review);
    }

    @Override
    public List<Review> myReviews(String customerEmail) {
        return reviews.findByCustomerId(customerIdOf(customerEmail));
    }

    @Override
    public List<OfferingRating> ratingsOf(Collection<UUID> offeringIds) {
        // Sin repetidos y en el orden pedido
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(offeringIds));
        if (ids.isEmpty()) return List.of();
        if (ids.size() > MAX_IDS_PER_REQUEST) {
            throw new BusinessRuleException("Se pueden consultar como máximo " + MAX_IDS_PER_REQUEST + " mentorías a la vez");
        }
        Map<UUID, RatingSummary> found = reviews.summarizeByOfferingIds(ids);
        return ids.stream()
            .map(id -> {
                RatingSummary summary = found.getOrDefault(id, RatingSummary.EMPTY);
                return new OfferingRating(id, summary.average(), summary.count());
            })
            .toList();
    }

    @Override
    public OfferingReviews reviewsOf(UUID offeringId) {
        // Mismo criterio que el detalle: una mentoría que el público no ve tampoco muestra sus calificaciones
        Offering offering = offerings.findVisibleById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));
        RatingSummary own = reviews.summarizeByOfferingIds(List.of(offeringId))
            .getOrDefault(offeringId, RatingSummary.EMPTY);
        return new OfferingReviews(own,
            reviews.summarizeMentor(offering.providerId()),
            reviews.findLatestCommentedByOfferingId(offeringId, RECENT_REVIEWS));
    }

    private UUID customerIdOf(String email) {
        return users.findIdByEmail(email)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
    }
}
