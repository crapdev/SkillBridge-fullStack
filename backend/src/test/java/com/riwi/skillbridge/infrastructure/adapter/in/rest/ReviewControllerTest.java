package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase;
import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase.OfferingRating;
import com.riwi.skillbridge.application.port.in.GetOfferingRatingsUseCase.OfferingReviews;
import com.riwi.skillbridge.application.port.in.RateBookingUseCase;
import com.riwi.skillbridge.application.port.in.RateBookingUseCase.ReviewDraft;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.ReviewAlreadyExistsException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.PublicReview;
import com.riwi.skillbridge.domain.model.RatingSummary;
import com.riwi.skillbridge.domain.model.Review;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import com.riwi.skillbridge.infrastructure.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCRUM-208 (HU-27): endpoints para calificar y consultar promedios. */
@WebMvcTest(controllers = {ReviewController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RateBookingUseCase rate;
    @MockitoBean private GetOfferingRatingsUseCase ratings;
    @MockitoBean private DatabaseUserDetailsService databaseUserDetailsService;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();
    private static final String BODY = """
            {"rating": 5, "comment": "Muy clara", "mentorRating": 4, "mentorComment": "Puntual"}
            """;

    // ---- POST /api/bookings/{id}/review ----

    @Test
    @WithMockUser(username = "ana@test.com", roles = "CUSTOMER")
    void customerRatesABooking() throws Exception {
        Review review = new Review(UUID.randomUUID(), bookingId, 5, "Muy clara", 4, "Puntual", Instant.now());
        when(rate.rate(bookingId, "ana@test.com", new ReviewDraft(5, "Muy clara", 4, "Puntual"))).thenReturn(review);

        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.mentorRating").value(4));
    }

    @Test
    @WithMockUser(username = "ana@test.com", roles = "CUSTOMER")
    void starsOutOfRangeReturn400() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 6, \"mentorRating\": 4}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rate);
    }

    @Test
    @WithMockUser(username = "ana@test.com", roles = "CUSTOMER")
    void missingMentorRatingReturns400() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rate);
    }

    @Test
    @WithMockUser(username = "ana@test.com", roles = "CUSTOMER")
    void ratingTwiceReturns409() throws Exception {
        when(rate.rate(eq(bookingId), eq("ana@test.com"), any())).thenThrow(new ReviewAlreadyExistsException());

        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya calificaste esta mentoría"));
    }

    @Test
    @WithMockUser(username = "ana@test.com", roles = "CUSTOMER")
    void notCompletedBookingReturns422() throws Exception {
        when(rate.rate(eq(bookingId), eq("ana@test.com"), any()))
                .thenThrow(new BusinessRuleException("Solo puedes calificar una mentoría después de completar la sesión"));

        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "ana@test.com", roles = "CUSTOMER")
    void bookingOfAnotherCustomerReturns403() throws Exception {
        when(rate.rate(eq(bookingId), eq("ana@test.com"), any()))
                .thenThrow(new UnauthorizedActionException("No puedes calificar una reserva de otro cliente"));

        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCannotRate() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rate);
    }

    @Test
    void withoutSessionCannotRate() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/review", bookingId).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ana@test.com", roles = "CUSTOMER")
    void listsMyReviews() throws Exception {
        when(rate.myReviews("ana@test.com"))
                .thenReturn(List.of(new Review(UUID.randomUUID(), bookingId, 4, null, 5, null, Instant.now())));

        mockMvc.perform(get("/api/bookings/me/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(bookingId.toString()));
    }

    // ---- Público: catálogo y detalle ----

    @Test
    void catalogRatingsArePublic() throws Exception {
        UUID other = UUID.randomUUID();
        when(ratings.ratingsOf(List.of(offeringId, other)))
                .thenReturn(List.of(new OfferingRating(offeringId, 4.5, 2), new OfferingRating(other, null, 0)));

        mockMvc.perform(get("/api/offerings/ratings").param("ids", offeringId + "," + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].average").value(4.5))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].average").doesNotExist())
                .andExpect(jsonPath("$[1].count").value(0));
    }

    @Test
    void detailReviewsArePublic() throws Exception {
        when(ratings.reviewsOf(offeringId)).thenReturn(new OfferingReviews(new RatingSummary(4.0, 3),
                new RatingSummary(4.8, 10), List.of(new PublicReview(5, "Excelente", 5, null, "Ana", Instant.now()))));

        mockMvc.perform(get("/api/offerings/{id}/reviews", offeringId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offering.average").value(4.0))
                .andExpect(jsonPath("$.mentor.count").value(10))
                .andExpect(jsonPath("$.recent[0].authorName").value("Ana"))
                .andExpect(jsonPath("$.recent[0].comment").value("Excelente"));
    }
}
