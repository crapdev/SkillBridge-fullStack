package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.exception.AiProviderException.Reason;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DeepSeekAiAdapterTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private DeepSeekAiAdapter adapter;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        adapter = new DeepSeekAiAdapter(builder);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    @DisplayName("Debe retornar la recomendación de DeepSeek cuando la respuesta es válida")
    void shouldReturnRecommendationWhenValid() {
        List<Offering> offerings = List.of(
            new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java Básico", "Introducción", "BACKEND", BigDecimal.TEN, true)
        );
        when(callResponseSpec.content()).thenReturn("Recomendación: Estudia Java Básico.");

        String result = adapter.recommend("Aprender Java", offerings);

        assertEquals("Recomendación: Estudia Java Básico.", result);
    }

    @Test
    @DisplayName("Respuesta vacía de DeepSeek → servicio no disponible")
    void shouldFailAsUnavailableWhenResponseIsEmpty() {
        when(callResponseSpec.content()).thenReturn("   ");

        assertReason(Reason.UNAVAILABLE, DeepSeekAiAdapter.UNAVAILABLE_MESSAGE);
    }

    @Test
    @DisplayName("401 de DeepSeek (API key inválida) → error de configuración")
    void shouldFailAsConfigurationOn401() {
        when(callResponseSpec.content()).thenThrow(new NonTransientAiException(
            "HTTP 401 - {\"error\":{\"message\":\"Authentication Fails, Your api key: ****abcd is invalid\"}}"));

        assertReason(Reason.CONFIGURATION, DeepSeekAiAdapter.CONFIGURATION_MESSAGE);
    }

    @Test
    @DisplayName("403 de DeepSeek (sin permisos) → error de configuración")
    void shouldFailAsConfigurationOn403() {
        when(callResponseSpec.content()).thenThrow(new NonTransientAiException("HTTP 403 - Forbidden"));

        assertReason(Reason.CONFIGURATION, DeepSeekAiAdapter.CONFIGURATION_MESSAGE);
    }

    @Test
    @DisplayName("429 de DeepSeek (cuota o límite) → demasiadas peticiones")
    void shouldFailAsRateLimitedOn429() {
        when(callResponseSpec.content()).thenThrow(new NonTransientAiException("HTTP 429 - Rate limit reached"));

        assertReason(Reason.RATE_LIMITED, DeepSeekAiAdapter.RATE_LIMITED_MESSAGE);
    }

    @Test
    @DisplayName("5xx de DeepSeek tras los reintentos → servicio no disponible")
    void shouldFailAsUnavailableOn5xx() {
        when(callResponseSpec.content()).thenThrow(new TransientAiException("HTTP 503 - Service Unavailable"));

        assertReason(Reason.UNAVAILABLE, DeepSeekAiAdapter.UNAVAILABLE_MESSAGE);
    }

    @Test
    @DisplayName("Timeout o red caída (sin respuesta HTTP) → servicio no disponible")
    void shouldFailAsUnavailableOnNetworkError() {
        when(callResponseSpec.content()).thenThrow(
            new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        assertReason(Reason.UNAVAILABLE, DeepSeekAiAdapter.UNAVAILABLE_MESSAGE);
    }

    @Test
    @DisplayName("El mensaje al usuario no expone el cuerpo de error de DeepSeek")
    void shouldNotExposeProviderErrorBody() {
        when(callResponseSpec.content()).thenThrow(new NonTransientAiException("HTTP 401 - Your api key: ****abcd is invalid"));

        AiProviderException ex = assertThrows(AiProviderException.class, () -> adapter.recommend("Objetivo", List.of()));

        assertFalse(ex.getMessage().contains("abcd"));
    }

    private void assertReason(Reason reason, String message) {
        AiProviderException ex = assertThrows(AiProviderException.class, () -> adapter.recommend("Objetivo", List.of()));
        assertEquals(reason, ex.reason());
        assertEquals(message, ex.getMessage());
    }
}
