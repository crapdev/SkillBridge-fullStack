package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.OfferingDraft;
import com.riwi.skillbridge.application.service.ProviderOfferingService;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.SyllabusTopic;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-175 (HU-20): contrato HTTP de la ficha completa — POST/PUT con temario, duración, nivel y días,
 * y las respuestas 400 de cada validación.
 */
@WebMvcTest(controllers = {ProviderOfferingController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "app.jwt.expiration=3600000"
})
class OfferingProfileControllerTest {

    private static final String EMAIL = "proveedor@email.com";
    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String URL = "/api/provider/offerings";
    private static final String TWO_TOPICS =
            "[{\"title\":\"Pods\",\"description\":\"Qué son\"},{\"title\":\"Deployments\",\"description\":\"Réplicas\"}]";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderOfferingService service;

    @MockitoBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    /** Cuerpo completo; cada prueba cambia solo el campo que quiere romper */
    private static String body(String syllabus, String duration, String level, String days) {
        return """
                {"title":"Kubernetes","description":"Primeros pasos","category":"CLOUD","price":90000,
                 "syllabus":%s,"durationMinutes":%s,"level":%s,"workingDays":%s}
                """.formatted(syllabus, duration, level, days);
    }

    private static String validBody() {
        return body(TWO_TOPICS, "90", "\"INTERMEDIO\"", "[\"WEDNESDAY\",\"MONDAY\"]");
    }

    private ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ==========================================
    // CREAR CON FICHA COMPLETA
    // ==========================================

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldCreateOfferingWithFullProfile() throws Exception {
        Offering created = new Offering(ID, UUID.randomUUID(), "Kubernetes", "Primeros pasos", "CLOUD", new BigDecimal("90000"),
                true, List.of(new SyllabusTopic("Pods", "Qué son"), new SyllabusTopic("Deployments", "Réplicas")), 90,
                OfferingLevel.INTERMEDIO, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        when(service.createOffering(any(OfferingDraft.class), eq(EMAIL))).thenReturn(created);

        postBody(validBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.syllabus.length()").value(2))
                .andExpect(jsonPath("$.syllabus[0].title").value("Pods"))
                .andExpect(jsonPath("$.durationMinutes").value(90))
                .andExpect(jsonPath("$.level").value("INTERMEDIO"))
                .andExpect(jsonPath("$.workingDays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.workingDays[1]").value("WEDNESDAY"));

        // El controlador entrega al caso de uso exactamente lo que llegó en la ficha
        verify(service).createOffering(argThat(d -> d.syllabus().size() == 2
                && d.syllabus().get(1).title().equals("Deployments")
                && d.durationMinutes() == 90
                && d.level() == OfferingLevel.INTERMEDIO
                && d.workingDays().equals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))), eq(EMAIL));
    }

    // ==========================================
    // TEMARIO: MÍNIMO 2 TEMAS (400)
    // ==========================================

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldReturn400_WhenSyllabusHasOneTopic() throws Exception {
        postBody(body("[{\"title\":\"Pods\",\"description\":\"Qué son\"}]", "90", "\"INTERMEDIO\"", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("syllabus: El temario debe tener al menos 2 temas"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void put_ShouldReturn400_WhenSyllabusIsEmpty() throws Exception {
        mockMvc.perform(put(URL + "/" + ID).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[]", "90", "\"INTERMEDIO\"", "[\"MONDAY\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("syllabus: El temario debe tener al menos 2 temas"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldReturn400_WhenSyllabusIsMissing() throws Exception {
        postBody(body("null", "90", "\"INTERMEDIO\"", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("syllabus: El temario es obligatorio"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldReturn400_WhenSyllabusHasMoreThanTwelveTopics() throws Exception {
        String thirteen = IntStream.rangeClosed(1, 13)
                .mapToObj(i -> "{\"title\":\"Tema " + i + "\",\"description\":\"Descripción\"}")
                .collect(Collectors.joining(",", "[", "]"));

        postBody(body(thirteen, "90", "\"INTERMEDIO\"", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("syllabus: El temario admite máximo 12 temas"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldReturn400_WhenATopicHasNoTitle() throws Exception {
        postBody(body("[{\"title\":\"Pods\",\"description\":\"Qué son\"},{\"title\":\" \",\"description\":\"Réplicas\"}]",
                "90", "\"INTERMEDIO\"", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("syllabus[1].title: Cada tema necesita un título"));
    }

    // ==========================================
    // DURACIÓN, NIVEL Y DÍAS (400)
    // ==========================================

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldReturn400_WhenDurationIsOutOfRange() throws Exception {
        postBody(body(TWO_TOPICS, "20", "\"INTERMEDIO\"", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("durationMinutes: La duración mínima es de 30 minutos"));
        postBody(body(TWO_TOPICS, "300", "\"INTERMEDIO\"", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("durationMinutes: La duración máxima es de 240 minutos"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldReturn400_WhenLevelIsMissingOrUnknown() throws Exception {
        postBody(body(TWO_TOPICS, "90", "null", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("level: El nivel es obligatorio"));
        postBody(body(TWO_TOPICS, "90", "\"EXPERTO\"", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("level: valor no permitido 'EXPERTO'. Usa uno de: BASICO, INTERMEDIO, AVANZADO"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void post_ShouldReturn400_WhenNoWorkingDayOrUnknownDay() throws Exception {
        postBody(body(TWO_TOPICS, "90", "\"BASICO\"", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("workingDays: Elige al menos un día de la semana"));
        postBody(body(TWO_TOPICS, "90", "\"BASICO\"", "[\"LUNES\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", startsWith("workingDays[0]: valor no permitido 'LUNES'")));
        verifyNoInteractions(service);
    }

    // ==========================================
    // HORARIOS EN UN DÍA NO OFRECIDO (422)
    // ==========================================

    @Test
    @WithMockUser(username = EMAIL, roles = "PROVIDER")
    void addSlot_ShouldReturn422_WhenDayIsNotOffered() throws Exception {
        when(service.addAvailabilitySlot(eq(ID), any(Instant.class), eq(EMAIL)))
                .thenThrow(new BusinessRuleException("Esta mentoría solo se ofrece los días lunes y miércoles. El sábado no está entre ellos"));

        mockMvc.perform(post(URL + "/" + ID + "/slots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2099-01-03T14:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Esta mentoría solo se ofrece los días lunes y miércoles. El sábado no está entre ellos"));
    }
}
