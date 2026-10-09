package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AI Recommendation Service Tests")
class AiRecommendationServiceTest {

    private final AiRecommendationPort aiPort = mock(AiRecommendationPort.class);
    private final OfferingRepositoryPort offeringRepo = mock(OfferingRepositoryPort.class);
    private final AiRecommendationService service = new AiRecommendationService(aiPort, offeringRepo);

    @Nested
    @DisplayName("Recommendation Generation")
    class RecommendationGenerationTests {

        @Test
        @DisplayName("Should obtain active offerings and delegate recommendation to AI port")
        void shouldDelegateRecommendationWithActiveOfferings() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Spring Boot Pro", "Curso avanzado", "BACKEND", BigDecimal.valueOf(50), true),
                new Offering(UUID.randomUUID(), "Angular Mastery", "Frontend moderno", "FRONTEND", BigDecimal.valueOf(45), true)
            );

            String goal = "Quiero aprender Java y Spring";
            String expectedRecommendation = "Te recomiendo Spring Boot Pro";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn(expectedRecommendation);

            // Act
            String result = service.recommend(goal);

            // Assert
            assertEquals(expectedRecommendation, result);
            verify(offeringRepo, times(1)).findAllActive();
            verify(aiPort, times(1)).recommend(goal, offerings);
        }

        @Test
        @DisplayName("Should return AI recommendation response")
        void shouldReturnAiRecommendationResponse() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "React Basics", "Intro a React", "FRONTEND", BigDecimal.valueOf(30), true)
            );
            String goal = "Aprender frontend moderno";
            String recommendation = "React Basics es perfecto para ti";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn(recommendation);

            // Act
            String result = service.recommend(goal);

            // Assert
            assertEquals(recommendation, result);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle recommendation with multiple offerings")
        void shouldHandleMultipleOfferings() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Java Basics", "Intro", "BACKEND", BigDecimal.valueOf(25), true),
                new Offering(UUID.randomUUID(), "Java Advanced", "Advanced", "BACKEND", BigDecimal.valueOf(50), true),
                new Offering(UUID.randomUUID(), "Spring Boot", "Framework", "BACKEND", BigDecimal.valueOf(55), true),
                new Offering(UUID.randomUUID(), "React", "Frontend", "FRONTEND", BigDecimal.valueOf(40), true)
            );
            String goal = "Quiero ser desarrollador full-stack";
            String expectedRec = "Te recomiendo Java Basics, Spring Boot y React";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn(expectedRec);

            // Act
            String result = service.recommend(goal);

            // Assert
            assertEquals(expectedRec, result);
            verify(aiPort).recommend(goal, offerings);
        }

        @Test
        @DisplayName("Should handle empty offerings list")
        void shouldHandleEmptyOfferingsList() {
            // Arrange
            var emptyOfferings = List.<Offering>of();
            String goal = "Aprender algo";
            String emptyRecommendation = "No hay cursos disponibles en este momento";

            when(offeringRepo.findAllActive()).thenReturn(emptyOfferings);
            when(aiPort.recommend(goal, emptyOfferings)).thenReturn(emptyRecommendation);

            // Act
            String result = service.recommend(goal);

            // Assert
            assertEquals(emptyRecommendation, result);
            verify(offeringRepo).findAllActive();
            verify(aiPort).recommend(goal, emptyOfferings);
        }

        @Test
        @DisplayName("Should pass goal unchanged to AI port")
        void shouldPassGoalUnchangedToAiPort() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );
            String goal = "Mi objetivo es aprender Docker";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn("Recomendación");

            // Act
            service.recommend(goal);

            // Assert
            verify(aiPort).recommend(goal, offerings);
        }

        @Test
        @DisplayName("Should pass all active offerings to AI port")
        void shouldPassAllActiveOfferingsToAiPort() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course1", "Desc1", "BACKEND", BigDecimal.TEN, true),
                new Offering(UUID.randomUUID(), "Course2", "Desc2", "FRONTEND", BigDecimal.valueOf(20), true),
                new Offering(UUID.randomUUID(), "Course3", "Desc3", "DEVOPS", BigDecimal.valueOf(30), true)
            );
            String goal = "Learn everything";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn("Recommendations");

            // Act
            service.recommend(goal);

            // Assert
            verify(aiPort).recommend(goal, offerings);
            assertEquals(3, offerings.size());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should propagate AI port exceptions")
        void shouldPropagateAiPortExceptions() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(anyString(), any())).thenThrow(new BusinessRuleException("AI service unavailable"));

            // Act & Assert
            assertThrows(BusinessRuleException.class, () -> service.recommend("goal"));
        }

        @Test
        @DisplayName("Should propagate repository exceptions")
        void shouldPropagateRepositoryExceptions() {
            // Arrange
            when(offeringRepo.findAllActive()).thenThrow(new RuntimeException("Database error"));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.recommend("goal"));
            verify(aiPort, never()).recommend(anyString(), any());
        }

        @Test
        @DisplayName("Should handle null recommendation from AI")
        void shouldHandleNullRecommendation() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(anyString(), any())).thenReturn(null);

            // Act
            String result = service.recommend("goal");

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle empty string recommendation from AI")
        void shouldHandleEmptyStringRecommendation() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(anyString(), any())).thenReturn("");

            // Act
            String result = service.recommend("goal");

            // Assert
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Goal Handling")
    class GoalHandlingTests {

        @Test
        @DisplayName("Should handle very long goals")
        void shouldHandleVeryLongGoals() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );
            String longGoal = "A".repeat(1000);

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(longGoal, offerings)).thenReturn("Recommendation");

            // Act
            String result = service.recommend(longGoal);

            // Assert
            assertNotNull(result);
            verify(aiPort).recommend(longGoal, offerings);
        }

        @Test
        @DisplayName("Should handle goals with special characters")
        void shouldHandleGoalsWithSpecialCharacters() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );
            String goal = "Quiero aprender C++, C#, y Python @2024 & más!";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn("Recommendation");

            // Act
            service.recommend(goal);

            // Assert
            verify(aiPort).recommend(goal, offerings);
        }

        @Test
        @DisplayName("Should handle goals with international characters")
        void shouldHandleGoalsWithInternationalCharacters() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );
            String goal = "Quiero aprender développement web français";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn("Recommendation");

            // Act
            service.recommend(goal);

            // Assert
            verify(aiPort).recommend(goal, offerings);
        }

        @Test
        @DisplayName("Should handle empty goal string")
        void shouldHandleEmptyGoalString() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend("", offerings)).thenReturn("Recommendation for empty goal");

            // Act
            String result = service.recommend("");

            // Assert
            assertNotNull(result);
            verify(aiPort).recommend("", offerings);
        }
    }

    @Nested
    @DisplayName("Offering Processing")
    class OfferingProcessingTests {

        @Test
        @DisplayName("Should use only active offerings")
        void shouldUseOnlyActiveOfferings() {
            // Arrange
            var activeOfferings = List.of(
                new Offering(UUID.randomUUID(), "Active Course", "Active", "BACKEND", BigDecimal.TEN, true)
            );

            when(offeringRepo.findAllActive()).thenReturn(activeOfferings);
            when(aiPort.recommend(anyString(), any())).thenReturn("Recommendation");

            // Act
            service.recommend("goal");

            // Assert
            verify(aiPort).recommend(anyString(), eq(activeOfferings));
        }

        @Test
        @DisplayName("Should handle single offering")
        void shouldHandleSingleOffering() {
            // Arrange
            var singleOffering = List.of(
                new Offering(UUID.randomUUID(), "Only Course", "Solo curso", "BACKEND", BigDecimal.valueOf(99), true)
            );

            when(offeringRepo.findAllActive()).thenReturn(singleOffering);
            when(aiPort.recommend(anyString(), any())).thenReturn("Recommendation for single course");

            // Act
            String result = service.recommend("goal");

            // Assert
            assertNotNull(result);
            verify(aiPort).recommend(anyString(), eq(singleOffering));
        }

        @Test
        @DisplayName("Should handle offerings with different categories")
        void shouldHandleOfferingsWithDifferentCategories() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Backend", "Backend course", "BACKEND", BigDecimal.TEN, true),
                new Offering(UUID.randomUUID(), "Frontend", "Frontend course", "FRONTEND", BigDecimal.valueOf(20), true),
                new Offering(UUID.randomUUID(), "DevOps", "DevOps course", "DEVOPS", BigDecimal.valueOf(30), true),
                new Offering(UUID.randomUUID(), "Fullstack", "Fullstack course", "FULLSTACK", BigDecimal.valueOf(40), true),
                new Offering(UUID.randomUUID(), "QA", "QA course", "QA", BigDecimal.valueOf(25), true)
            );

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(anyString(), any())).thenReturn("Recommendations across categories");

            // Act
            service.recommend("I want to learn everything");

            // Assert
            verify(aiPort).recommend(anyString(), eq(offerings));
        }

        @Test
        @DisplayName("Should handle offerings with varying prices")
        void shouldHandleOfferingsWithVaryingPrices() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Free", "No cost", "BACKEND", BigDecimal.ZERO, true),
                new Offering(UUID.randomUUID(), "Cheap", "Low cost", "FRONTEND", BigDecimal.valueOf(10), true),
                new Offering(UUID.randomUUID(), "Medium", "Medium cost", "BACKEND", BigDecimal.valueOf(100), true),
                new Offering(UUID.randomUUID(), "Expensive", "High cost", "FRONTEND", BigDecimal.valueOf(500), true)
            );

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(anyString(), any())).thenReturn("Recommendations with budget options");

            // Act
            service.recommend("Find courses in different price ranges");

            // Assert
            verify(aiPort).recommend(anyString(), eq(offerings));
        }
    }

    @Nested
    @DisplayName("Mock Interactions")
    class MockInteractionsTests {

        @Test
        @DisplayName("Should call repository exactly once per recommendation")
        void shouldCallRepositoryExactlyOnce() {
            // Arrange
            when(offeringRepo.findAllActive()).thenReturn(List.of());
            when(aiPort.recommend(anyString(), any())).thenReturn("Rec");

            // Act
            service.recommend("goal");

            // Assert
            verify(offeringRepo, times(1)).findAllActive();
        }

        @Test
        @DisplayName("Should call AI port exactly once per recommendation")
        void shouldCallAiPortExactlyOnce() {
            // Arrange
            when(offeringRepo.findAllActive()).thenReturn(List.of());
            when(aiPort.recommend(anyString(), any())).thenReturn("Rec");

            // Act
            service.recommend("goal");

            // Assert
            verify(aiPort, times(1)).recommend(anyString(), any());
        }

        @Test
        @DisplayName("Should not call repository multiple times for single recommendation request")
        void shouldNotCallRepositoryMultipleTimes() {
            // Arrange
            when(offeringRepo.findAllActive()).thenReturn(List.of());
            when(aiPort.recommend(anyString(), any())).thenReturn("Rec");

            // Act
            service.recommend("goal");
            service.recommend("goal");

            // Assert
            verify(offeringRepo, times(2)).findAllActive();
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenariosTests {

        @Test
        @DisplayName("Should handle complete recommendation workflow")
        void shouldHandleCompleteRecommendationWorkflow() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Spring Boot", "Spring framework", "BACKEND", BigDecimal.valueOf(60), true),
                new Offering(UUID.randomUUID(), "Vue.js", "Vue frontend", "FRONTEND", BigDecimal.valueOf(50), true),
                new Offering(UUID.randomUUID(), "Docker", "Container tech", "DEVOPS", BigDecimal.valueOf(55), true)
            );
            String goal = "I want to become a full-stack developer";
            String recommendation = "Based on your goal, I recommend: Spring Boot for backend, Vue.js for frontend, and Docker for deployment";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(goal, offerings)).thenReturn(recommendation);

            // Act
            String result = service.recommend(goal);

            // Assert
            assertEquals(recommendation, result);
            verify(offeringRepo).findAllActive();
            verify(aiPort).recommend(goal, offerings);
        }

        @Test
        @DisplayName("Should handle sequential recommendations")
        void shouldHandleSequentialRecommendations() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Course", "Desc", "BACKEND", BigDecimal.TEN, true)
            );

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(anyString(), any())).thenReturn("Recommendation");

            // Act
            String result1 = service.recommend("goal1");
            String result2 = service.recommend("goal2");
            String result3 = service.recommend("goal3");

            // Assert
            assertEquals("Recommendation", result1);
            assertEquals("Recommendation", result2);
            assertEquals("Recommendation", result3);
            verify(offeringRepo, times(3)).findAllActive();
            verify(aiPort, times(3)).recommend(anyString(), any());
        }

        @Test
        @DisplayName("Should handle recommendation with complex goal and multiple offerings")
        void shouldHandleComplexScenario() {
            // Arrange
            var offerings = List.of(
                new Offering(UUID.randomUUID(), "Java", "Java basics", "BACKEND", BigDecimal.valueOf(40), true),
                new Offering(UUID.randomUUID(), "Spring", "Spring framework", "BACKEND", BigDecimal.valueOf(50), true),
                new Offering(UUID.randomUUID(), "React", "React library", "FRONTEND", BigDecimal.valueOf(45), true),
                new Offering(UUID.randomUUID(), "Node", "Node.js", "BACKEND", BigDecimal.valueOf(35), true),
                new Offering(UUID.randomUUID(), "Docker", "Containerization", "DEVOPS", BigDecimal.valueOf(60), true),
                new Offering(UUID.randomUUID(), "Kubernetes", "Orchestration", "DEVOPS", BigDecimal.valueOf(75), true)
            );
            String complexGoal = "I want to build scalable microservices with containerization and cloud deployment";

            when(offeringRepo.findAllActive()).thenReturn(offerings);
            when(aiPort.recommend(complexGoal, offerings)).thenReturn("Complex recommendation");

            // Act
            String result = service.recommend(complexGoal);

            // Assert
            assertNotNull(result);
            verify(aiPort).recommend(complexGoal, offerings);
        }
    }
}
