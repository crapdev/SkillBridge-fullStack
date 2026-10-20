package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase;
import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase.BookingCounts;
import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase.OfferingCounts;
import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase.ProviderStats;
import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase.SlotCounts;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCRUM-187 (HU-22): GET /api/provider/stats, solo para el proveedor y con sus propios datos. */
@WebMvcTest(controllers = {ProviderStatsController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class ProviderStatsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GetProviderStatsUseCase stats;
    @MockitoBean private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    @WithMockUser(username = "laura@test.com", roles = "PROVIDER")
    void returnsTheStatsOfTheAuthenticatedProvider() throws Exception {
        when(stats.statsFor("laura@test.com")).thenReturn(new ProviderStats(
                new OfferingCounts(4, 2, 1, 1, 1), new SlotCounts(5, 2), new BookingCounts(6, 2, 1, 1, 3, 1)));

        mockMvc.perform(get("/api/provider/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerings.pendingReview").value(1))
                .andExpect(jsonPath("$.offerings.approvedWithoutFreeSlots").value(1))
                .andExpect(jsonPath("$.slots.upcomingFree").value(5))
                .andExpect(jsonPath("$.bookings.upcoming").value(2));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotSeeProviderStats() throws Exception {
        mockMvc.perform(get("/api/provider/stats")).andExpect(status().isForbidden());
        verifyNoInteractions(stats);
    }

    @Test
    void withoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/provider/stats")).andExpect(status().isUnauthorized());
    }
}
