package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.service.ProviderOfferingService;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-168 (HU-19): contrato HTTP de la edición y eliminación de mentorías del proveedor.
 */
@WebMvcTest(controllers = {ProviderOfferingController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class ProviderOfferingControllerTest {

    private static final String EMAIL = "proveedor@email.com";
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String URL = "/api/provider/offerings/" + ID;
    private static final String VALID_BODY = """
            {"title":"Spring Boot avanzado","description":"Seguridad y JPA","category":"BACKEND","price":120000}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderOfferingService service;

    @MockitoBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    // ==========================================
    // PUT /api/provider/offerings/{id}
    // ==========================================

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void put_ShouldReturnUpdatedOffering_WhenOwnerSendsValidData() throws Exception {
        Offering updated = new Offering(ID, UUID.randomUUID(), "Spring Boot avanzado", "Seguridad y JPA",
                "BACKEND", new BigDecimal("120000"), true);
        when(service.updateOffering(eq(ID), eq("Spring Boot avanzado"), eq("Seguridad y JPA"), eq("BACKEND"),
                any(BigDecimal.class), eq(EMAIL))).thenReturn(updated);

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.title").value("Spring Boot avanzado"))
                .andExpect(jsonPath("$.price").value(120000));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void put_ShouldReturn403_WhenOfferingBelongsToAnotherProvider() throws Exception {
        when(service.updateOffering(eq(ID), any(), any(), any(), any(), eq(EMAIL)))
                .thenThrow(new UnauthorizedActionException("No tienes permisos para modificar una mentoría de otro proveedor"));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes permisos para modificar una mentoría de otro proveedor"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void put_ShouldReturn404_WhenOfferingDoesNotExist() throws Exception {
        when(service.updateOffering(eq(ID), any(), any(), any(), any(), eq(EMAIL)))
                .thenThrow(new DomainNotFoundException("Mentoría no encontrada"));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void put_ShouldReturn400_WhenPriceIsZero() throws Exception {
        String body = """
                {"title":"Java","description":"Mentoría","category":"BACKEND","price":0}
                """;

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("price: El precio debe ser mayor a cero"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void put_ShouldReturn400_WhenRequiredFieldIsBlank() throws Exception {
        String body = """
                {"title":"   ","description":"Mentoría","category":"BACKEND","price":50000}
                """;

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("title: El título es obligatorio"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void put_ShouldReturn400_WhenPriceIsMissing() throws Exception {
        String body = """
                {"title":"Java","description":"Mentoría","category":"BACKEND"}
                """;

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "cliente@email.com", roles = "CUSTOMER")
    void put_ShouldReturn403_WhenUserIsNotProvider() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void put_ShouldReturn401_WhenThereIsNoToken() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    // ==========================================
    // DELETE /api/provider/offerings/{id}
    // ==========================================

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void delete_ShouldReturn204_WhenOfferingHasNoReservedSlots() throws Exception {
        mockMvc.perform(delete(URL))
                .andExpect(status().isNoContent());
        verify(service).deleteOffering(ID, EMAIL);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void delete_ShouldReturn422WithExplanation_WhenOfferingHasReservedSlots() throws Exception {
        doThrow(new BusinessRuleException("No puedes eliminar una mentoría que tiene horarios reservados"))
                .when(service).deleteOffering(ID, EMAIL);

        mockMvc.perform(delete(URL))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("No puedes eliminar una mentoría que tiene horarios reservados"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void delete_ShouldReturn403_WhenOfferingBelongsToAnotherProvider() throws Exception {
        doThrow(new UnauthorizedActionException("No tienes permisos para modificar una mentoría de otro proveedor"))
                .when(service).deleteOffering(ID, EMAIL);

        mockMvc.perform(delete(URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "cliente@email.com", roles = "CUSTOMER")
    void delete_ShouldReturn403_WhenUserIsNotProvider() throws Exception {
        mockMvc.perform(delete(URL))
                .andExpect(status().isForbidden());
        verify(service, never()).deleteOffering(any(), any());
    }
}
