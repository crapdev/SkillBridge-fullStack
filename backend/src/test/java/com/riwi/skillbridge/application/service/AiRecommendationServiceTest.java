package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AiRecommendationServiceTest {

    @Test
    @DisplayName("Debe obtener ofertas activas y delegar la recomendación al puerto de IA")
    void shouldDelegateRecommendationWithActiveOfferings() {
        // Arrange
        AiRecommendationPort aiPort = mock(AiRecommendationPort.class);
        OfferingRepositoryPort offeringRepo = mock(OfferingRepositoryPort.class);

        var offerings = List.of(
            new Offering(UUID.randomUUID(), "Spring Boot Pro", "Curso avanzado", "BACKEND", BigDecimal.valueOf(50), true),
            new Offering(UUID.randomUUID(), "Angular Mastery", "Frontend moderno", "FRONTEND", BigDecimal.valueOf(45), true)
        );

        String goal = "Quiero aprender Java y Spring";
        String expectedRecommendation = "Te recomiendo Spring Boot Pro";

        when(offeringRepo.findAllActive()).thenReturn(offerings);
        when(aiPort.recommend(goal, offerings)).thenReturn(expectedRecommendation);

        var service = new AiRecommendationService(aiPort, offeringRepo);

        // Act
        String result = service.recommend(goal);

        // Assert
        assertEquals(expectedRecommendation, result);
        verify(offeringRepo, times(1)).findAllActive();
        verify(aiPort, times(1)).recommend(goal, offerings);
    }
}
