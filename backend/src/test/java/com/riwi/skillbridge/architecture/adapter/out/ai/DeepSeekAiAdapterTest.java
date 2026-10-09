package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
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
    }

    @Test
    @DisplayName("Debe retornar la recomendación de DeepSeek cuando la respuesta es válida")
    void shouldReturnRecommendationWhenValid() {
        List<Offering> offerings = List.of(
            new Offering(UUID.randomUUID(), "Java Básico", "Introducción", "BACKEND", BigDecimal.TEN, true)
        );

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Recomendación: Estudia Java Básico.");

        String result = adapter.recommend("Aprender Java", offerings);

        assertEquals("Recomendación: Estudia Java Básico.", result);
    }

    @Test
    @DisplayName("Debe lanzar BusinessRuleException si DeepSeek devuelve texto vacío o nulo")
    void shouldThrowExceptionWhenResponseIsEmpty() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("   ");

        assertThrows(BusinessRuleException.class, () ->
            adapter.recommend("Objetivo", List.of())
        );
    }

    @Test
    @DisplayName("Debe capturar RuntimeException de red y convertirla en BusinessRuleException")
    void shouldWrapRuntimeExceptionIntoBusinessRuleException() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenThrow(new RuntimeException("SSL error o red caída"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
            adapter.recommend("Objetivo", List.of())
        );

        assertEquals("DeepSeek no está disponible; inténtalo de nuevo", ex.getMessage());
    }
}
