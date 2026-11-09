package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.Reason;
import com.riwi.skillbridge.domain.model.ChatMessage;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AI Recommendation Service Tests")
class AiRecommendationServiceTest {

    private final AiRecommendationPort aiPort = mock(AiRecommendationPort.class);
    private final OfferingRepositoryPort offeringRepo = mock(OfferingRepositoryPort.class);
    private final AiRecommendationService service = new AiRecommendationService(aiPort, offeringRepo);

    private static Offering offering(String title) {
        return new Offering(UUID.randomUUID(), UUID.randomUUID(), title, "Descripción", "BACKEND", BigDecimal.TEN, true);
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> sentConversation() {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiPort).recommend(captor.capture(), anyList());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Catálogo visible (HU-17 / BUG-02)")
    class VisibleCatalogTests {

        @Test
        @DisplayName("Debe enviar a la IA solo las mentorías visibles")
        void shouldSendVisibleOfferingsToAi() {
            // Arrange
            var visible = List.of(offering("Spring Boot Pro"), offering("Angular Mastery"));
            when(offeringRepo.findVisible()).thenReturn(visible);
            when(aiPort.recommend(anyList(), any())).thenReturn("Te recomiendo Spring Boot Pro");

            // Act
            String result = service.recommend("Quiero aprender Spring", List.of());

            // Assert
            assertEquals("Te recomiendo Spring Boot Pro", result);
            verify(offeringRepo).findVisible();
            verify(aiPort).recommend(anyList(), eq(visible));
        }

        @Test
        @DisplayName("Con catálogo vacío no debe invocar el puerto de IA (BUG-02)")
        void shouldNotCallAiWhenCatalogIsEmpty() {
            // Arrange
            when(offeringRepo.findVisible()).thenReturn(List.of());

            // Act
            String result = service.recommend("Aprender algo", List.of());

            // Assert
            assertEquals(AiRecommendationService.EMPTY_CATALOG_MESSAGE, result);
            verify(aiPort, never()).recommend(anyList(), any());
        }
    }

    @Nested
    @DisplayName("Historial de la conversación")
    class HistoryTests {

        @Test
        @DisplayName("Debe enviar el historial y al final el mensaje actual del usuario")
        void shouldSendHistoryFollowedByCurrentMessage() {
            // Arrange
            when(offeringRepo.findVisible()).thenReturn(List.of(offering("Spring Boot Pro")));
            when(aiPort.recommend(anyList(), any())).thenReturn("Respuesta");
            var history = List.of(
                ChatMessage.user("Quiero aprender backend"),
                ChatMessage.assistant("Te recomiendo Spring Boot Pro")
            );

            // Act
            service.recommend("¿Y algo más barato?", history);

            // Assert
            assertEquals(List.of(
                ChatMessage.user("Quiero aprender backend"),
                ChatMessage.assistant("Te recomiendo Spring Boot Pro"),
                ChatMessage.user("¿Y algo más barato?")
            ), sentConversation());
        }

        @Test
        @DisplayName("Sin historial (null) envía solo el mensaje actual")
        void shouldSendOnlyCurrentMessageWhenHistoryIsNull() {
            when(offeringRepo.findVisible()).thenReturn(List.of(offering("Spring Boot Pro")));
            when(aiPort.recommend(anyList(), any())).thenReturn("Respuesta");

            service.recommend("Hola", null);

            assertEquals(List.of(ChatMessage.user("Hola")), sentConversation());
        }

        @Test
        @DisplayName("Debe enviar solo los últimos mensajes del historial y descartar los más antiguos")
        void shouldKeepOnlyLastHistoryMessages() {
            // Arrange
            when(offeringRepo.findVisible()).thenReturn(List.of(offering("Spring Boot Pro")));
            when(aiPort.recommend(anyList(), any())).thenReturn("Respuesta");
            int total = AiRecommendationService.MAX_HISTORY_MESSAGES + 5;
            var history = IntStream.rangeClosed(1, total).mapToObj(i -> ChatMessage.user("mensaje " + i)).toList();

            // Act
            service.recommend("actual", history);

            // Assert
            List<ChatMessage> sent = sentConversation();
            assertEquals(AiRecommendationService.MAX_HISTORY_MESSAGES + 1, sent.size());
            assertEquals(ChatMessage.user("mensaje 6"), sent.get(0));
            assertEquals(ChatMessage.user("mensaje " + total), sent.get(sent.size() - 2));
            assertEquals(ChatMessage.user("actual"), sent.get(sent.size() - 1));
        }
    }

    @Nested
    @DisplayName("Enlaces a las mentorías")
    class LinkTests {

        @Test
        @DisplayName("Mantiene los enlaces a mentorías del catálogo visible")
        void shouldKeepLinksToVisibleOfferings() {
            Offering spring = offering("Spring Boot Pro");
            when(offeringRepo.findVisible()).thenReturn(List.of(spring));
            String response = "Te recomiendo [Spring Boot Pro](/services/%s).".formatted(spring.id());
            when(aiPort.recommend(anyList(), any())).thenReturn(response);

            assertEquals(response, service.recommend("Spring", List.of()));
        }

        @Test
        @DisplayName("Quita el enlace si el id no está en el catálogo visible y deja el título")
        void shouldRemoveLinksToUnknownOfferings() {
            Offering spring = offering("Spring Boot Pro");
            when(offeringRepo.findVisible()).thenReturn(List.of(spring));
            when(aiPort.recommend(anyList(), any())).thenReturn(
                "1. [Spring Boot Pro](/services/%s)\n2. [Curso inventado](/services/%s)"
                    .formatted(spring.id(), UUID.randomUUID()));

            String result = service.recommend("Spring", List.of());

            assertEquals("1. [Spring Boot Pro](/services/%s)\n2. Curso inventado".formatted(spring.id()), result);
        }

        @Test
        @DisplayName("Acepta el id aunque el modelo lo escriba en mayúsculas")
        void shouldAcceptUppercaseIds() {
            Offering spring = offering("Spring Boot Pro");
            when(offeringRepo.findVisible()).thenReturn(List.of(spring));
            String response = "[Spring Boot Pro](/services/%s)".formatted(spring.id().toString().toUpperCase());
            when(aiPort.recommend(anyList(), any())).thenReturn(response);

            assertEquals(response, service.recommend("Spring", List.of()));
        }
    }

    @Nested
    @DisplayName("Manejo de errores")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Propaga los errores del proveedor de IA (HU-12)")
        void shouldPropagateAiProviderExceptions() {
            when(offeringRepo.findVisible()).thenReturn(List.of(offering("Course")));
            when(aiPort.recommend(anyList(), any())).thenThrow(new AiProviderException(Reason.UNAVAILABLE, "No disponible"));

            assertThrows(AiProviderException.class, () -> service.recommend("goal", List.of()));
        }

        @Test
        @DisplayName("Si falla el repositorio no consulta a la IA")
        void shouldNotCallAiWhenRepositoryFails() {
            when(offeringRepo.findVisible()).thenThrow(new RuntimeException("Database error"));

            assertThrows(RuntimeException.class, () -> service.recommend("goal", List.of()));
            verify(aiPort, never()).recommend(anyList(), any());
        }
    }
}
