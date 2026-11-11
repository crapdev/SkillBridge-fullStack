package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase;
import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase.OfferingRating;
import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase.OfferingReviews;
import com.riwi.skillbridge.application.port.in.RateBookingUseCase;
import com.riwi.skillbridge.domain.model.Review;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.RateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HU-27: calificaciones.
 * - /api/bookings/**: solo CUSTOMER (regla de SecurityConfiguration); el cliente sale del token.
 * - GET /api/offerings/**: público, como el catálogo y el detalle.
 */
@RestController
public class ReviewController {
    private final RateBookingUseCase rate;
    private final GetOfferingRatingsUseCase ratings;

    public ReviewController(RateBookingUseCase rate, GetOfferingRatingsUseCase ratings) {
        this.rate = rate;
        this.ratings = ratings;
    }

    @PostMapping("/api/bookings/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public Review rate(@PathVariable UUID id, @Valid @RequestBody RateBookingRequest request,
                       Authentication authentication) {
        return rate.rate(id, authentication.getName(), request.toDraft());
    }

    @GetMapping("/api/bookings/me/reviews")
    public List<Review> myReviews(Authentication authentication) {
        return rate.myReviews(authentication.getName());
    }

    /** Catálogo: promedio y cantidad de varias mentorías en una sola petición (?ids=a,b,c). */
    @GetMapping("/api/offerings/ratings")
    public List<OfferingRating> ratings(@RequestParam List<UUID> ids) {
        return ratings.ratingsOf(ids);
    }

    /** Detalle: promedio de la mentoría y del mentor, y los comentarios más recientes. */
    @GetMapping("/api/offerings/{id}/reviews")
    public OfferingReviews reviews(@PathVariable UUID id) {
        return ratings.reviewsOf(id);
    }
}
