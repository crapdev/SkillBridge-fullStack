package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.GenerateRecommendationUseCase;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.Reason;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AiController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class AiControllerTest {

    private static final String BODY = "{\"goal\":\"Quiero aprender Spring Boot\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerateRecommendationUseCase useCase;

    @MockitoBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturnRecommendation() throws Exception {
        when(useCase.recommend(anyString())).thenReturn("Te recomiendo Java Básico");

        mockMvc.perform(post("/api/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("Te recomiendo Java Básico"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturn429WhenRateLimited() throws Exception {
        when(useCase.recommend(anyString())).thenThrow(new AiProviderException(Reason.RATE_LIMITED, "Demasiadas consultas"));

        mockMvc.perform(post("/api/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Demasiadas consultas"))
                .andExpect(jsonPath("$.reason").value("RATE_LIMITED"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturn503WhenMisconfigured() throws Exception {
        when(useCase.recommend(anyString())).thenThrow(new AiProviderException(Reason.CONFIGURATION, "No configurado"));

        mockMvc.perform(post("/api/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("CONFIGURATION"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldReturn503WhenUnavailable() throws Exception {
        when(useCase.recommend(anyString())).thenThrow(new AiProviderException(Reason.UNAVAILABLE, "No disponible"));

        mockMvc.perform(post("/api/ai/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("No disponible"))
                .andExpect(jsonPath("$.reason").value("UNAVAILABLE"));
    }
}
