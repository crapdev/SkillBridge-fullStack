package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListMyBookingsUseCase;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.SlotAlreadyTakenException;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BUG-08: contrato HTTP cuando dos clientes reservan la misma franja al mismo tiempo. */
@WebMvcTest(controllers = {BookingController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class BookingControllerTest {

    private static final String EMAIL = "cliente@email.com";
    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateBookingUseCase createBooking;

    @MockitoBean
    private ListMyBookingsUseCase listMyBookings;

    @MockitoBean
    private CancelBookingUseCase cancelBooking;

    @MockitoBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    private String body() {
        Instant scheduledAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        return """
                {"offeringId":"%s","scheduledAt":"%s"}
                """.formatted(OFFERING_ID, scheduledAt);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "CUSTOMER")
    void shouldReturn409WhenAnotherCustomerTookTheSlotAtTheSameTime() throws Exception {
        when(createBooking.create(eq(OFFERING_ID), any(), eq(EMAIL))).thenThrow(new SlotAlreadyTakenException());

        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este horario acaba de ser apartado por otro usuario. Elige otro horario."));
    }

    // Un horario que ya estaba reservado antes de intentarlo sigue siendo una regla de negocio (422)
    @Test
    @WithMockUser(username = EMAIL, roles = "CUSTOMER")
    void shouldKeep422WhenTheSlotWasAlreadyReserved() throws Exception {
        when(createBooking.create(eq(OFFERING_ID), any(), eq(EMAIL)))
                .thenThrow(new BusinessRuleException("Este horario ya fue reservado por otro usuario"));

        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnprocessableEntity());
    }
}
