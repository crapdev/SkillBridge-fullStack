package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ReviewOfferingsUseCase;
import com.riwi.skillbridge.application.port.in.ReviewOfferingsUseCase.OfferingForReview;
import com.riwi.skillbridge.domain.model.Offering;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCRUM-179 (HU-21): endpoints de revisión de mentorías, solo para ADMIN. */
@WebMvcTest(controllers = {AdminOfferingReviewController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class AdminOfferingReviewControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReviewOfferingsUseCase review;
    @MockitoBean private DatabaseUserDetailsService databaseUserDetailsService;

    private final UUID offeringId = UUID.randomUUID();

    private OfferingForReview pending() {
        Offering offering = new Offering(offeringId, UUID.randomUUID(), "Kubernetes", "Pods", "CLOUD",
                new BigDecimal("130000"), true).pendingReview();
        return new OfferingForReview(offering, "Andrés Ruiz", "andres@test.com");
    }

    private static String body(String decision, String reason) {
        return reason == null ? "{\"decision\":\"%s\"}".formatted(decision)
                : "{\"decision\":\"%s\",\"reason\":\"%s\"}".formatted(decision, reason);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void provider_cannotReview() throws Exception {
        mockMvc.perform(get("/api/admin/offerings/pending")).andExpect(status().isForbidden());
        verifyNoInteractions(review);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listPending_returnsTheOfferingAndItsProvider() throws Exception {
        when(review.listPending()).thenReturn(List.of(pending()));

        mockMvc.perform(get("/api/admin/offerings/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(offeringId.toString()))
                .andExpect(jsonPath("$[0].reviewStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[0].providerName").value("Andrés Ruiz"))
                .andExpect(jsonPath("$[0].providerEmail").value("andres@test.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approve() throws Exception {
        when(review.approve(offeringId)).thenReturn(pending());

        mockMvc.perform(patch("/api/admin/offerings/{id}/review", offeringId)
                        .contentType(MediaType.APPLICATION_JSON).content(body("APPROVED", null)))
                .andExpect(status().isOk());
        verify(review).approve(offeringId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reject_withReason() throws Exception {
        when(review.reject(offeringId, "Falta el temario")).thenReturn(pending());

        mockMvc.perform(patch("/api/admin/offerings/{id}/review", offeringId)
                        .contentType(MediaType.APPLICATION_JSON).content(body("REJECTED", "Falta el temario")))
                .andExpect(status().isOk());
        verify(review).reject(offeringId, "Falta el temario");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendingIsNotADecision() throws Exception {
        mockMvc.perform(patch("/api/admin/offerings/{id}/review", offeringId)
                        .contentType(MediaType.APPLICATION_JSON).content(body("PENDING_REVIEW", null)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(review);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingDecisionOrTooLongReason_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/offerings/{id}/review", offeringId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/offerings/{id}/review", offeringId)
                        .contentType(MediaType.APPLICATION_JSON).content(body("REJECTED", "x".repeat(301))))
                .andExpect(status().isBadRequest());
        verify(review, org.mockito.Mockito.never()).reject(any(), any());
    }
}
