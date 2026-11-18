package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.service.AnalyticsService;
import com.riwi.skillbridge.infrastructure.adapter.out.analytics.AnalyticsReaderAdapter;
import com.riwi.skillbridge.infrastructure.adapter.out.analytics.PostgresAnalyticsReader;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import com.riwi.skillbridge.infrastructure.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAnalyticsController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, JwtService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        AnalyticsService.class, AnalyticsReaderAdapter.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class AdminAnalyticsControllerTest {
    private static final String URL = "/api/admin/analytics";

    @TempDir static Path outputDirectory;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private DatabaseUserDetailsService databaseUserDetailsService;
    @MockitoBean private PostgresAnalyticsReader postgresAnalyticsReader;

    @DynamicPropertySource
    static void analyticsDirectory(DynamicPropertyRegistry registry) {
        registry.add("ANALYTICS_OUTPUT_DIR", () -> outputDirectory.toString());
    }

    @BeforeEach
    void clearResults() throws IOException {
        Files.deleteIfExists(outputDirectory.resolve("analytics-fixtures.json"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsSnapshotByDefaultAndSupportsExplicitFixtures() throws Exception {
        publish("fixtures");
        when(postgresAnalyticsReader.read()).thenReturn(Map.of(
                "dataset", "snapshot", "source", "PostgreSQL",
                "tasaCancelacion", List.of(Map.of("provider_name", "prov_02", "tasa_cancelacion", 12.5))));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dataset").value("snapshot"))
                .andExpect(jsonPath("$.source").value("PostgreSQL"))
                .andExpect(jsonPath("$.tasaCancelacion[0].provider_name").value("prov_02"))
                .andExpect(jsonPath("$.tasaCancelacion[0].tasa_cancelacion").value(12.5));
        mockMvc.perform(get(URL).param("dataset", "fixtures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataset").value("fixtures"));
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void deniesProviders() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsUnsupportedOrEmptyDataset() throws Exception {
        for (String dataset : new String[]{"unknown", "", "../snapshot"}) {
            mockMvc.perform(get(URL).param("dataset", dataset)).andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsProblemDetailWhenRequestedDatasetIsMissing() throws Exception {
        mockMvc.perform(get(URL).param("dataset", "fixtures"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Aún no hay resultados del pipeline de datos"));
    }

    private void publish(String dataset) throws IOException {
        Files.writeString(outputDirectory.resolve("analytics-" + dataset + ".json"), """
                {
                  "dataset": "%s",
                  "generatedAt": "2026-11-17T12:00:00Z",
                  "sparkVersion": "4.0.0",
                  "source": "bookings.csv",
                  "reservasPorEstado": [],
                  "tasaCancelacion": [
                    {"provider_id": "02", "provider_name": "prov_02", "reservas": 8,
                     "canceladas": 1, "tasa_cancelacion": 12.5}
                  ],
                  "completitud": [],
                  "proximas": []
                }
                """.formatted(dataset));
    }
}
