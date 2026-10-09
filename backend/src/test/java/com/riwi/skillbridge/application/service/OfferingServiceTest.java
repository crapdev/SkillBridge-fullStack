package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("OfferingService Tests")
class OfferingServiceTest {

    private final OfferingRepositoryPort repository = mock(OfferingRepositoryPort.class);
    private final OfferingCachePort cache = mock(OfferingCachePort.class);
    private final OfferingService service = new OfferingService(repository, cache);

    @Nested
    @DisplayName("List Active Offerings Functionality")
    class ListActiveOfferingsTests {

        @Test
        @DisplayName("Should return cached offerings when cache hit")
        void shouldReturnCachedOfferingsOnHit() {
            // Arrange
            List<Offering> cachedOfferings = List.of(
                new Offering(UUID.randomUUID(), "Java Basics", "Introduction to Java", "BACKEND", BigDecimal.valueOf(30), true),
                new Offering(UUID.randomUUID(), "React Advanced", "Advanced React patterns", "FRONTEND", BigDecimal.valueOf(50), true)
            );

            when(cache.getActiveOfferings()).thenReturn(Optional.of(cachedOfferings));

            // Act
            List<Offering> result = service.listActive();

            // Assert
            assertEquals(cachedOfferings, result);
            assertEquals(2, result.size());
            verify(cache).getActiveOfferings();
            verify(repository, never()).findAllActive();
            verify(cache, never()).putActiveOfferings(any());
        }

        @Test
        @DisplayName("Should fetch from repository and cache when cache miss")
        void shouldFetchFromRepositoryOnCacheMiss() {
            // Arrange
            List<Offering> repositoryOfferings = List.of(
                new Offering(UUID.randomUUID(), "Python Basics", "Introduction to Python", "BACKEND", BigDecimal.valueOf(25), true),
                new Offering(UUID.randomUUID(), "Vue.js Mastery", "Advanced Vue patterns", "FRONTEND", BigDecimal.valueOf(45), true),
                new Offering(UUID.randomUUID(), "DevOps Essentials", "Docker and Kubernetes", "DEVOPS", BigDecimal.valueOf(60), true)
            );

            when(cache.getActiveOfferings()).thenReturn(Optional.empty());
            when(repository.findAllActive()).thenReturn(repositoryOfferings);

            // Act
            List<Offering> result = service.listActive();

            // Assert
            assertEquals(repositoryOfferings, result);
            assertEquals(3, result.size());
            verify(cache).getActiveOfferings();
            verify(repository).findAllActive();
            verify(cache).putActiveOfferings(repositoryOfferings);
        }

        @Test
        @DisplayName("Should return empty list when no active offerings exist")
        void shouldReturnEmptyListWhenNoOfferingsExist() {
            // Arrange
            List<Offering> emptyList = List.of();

            when(cache.getActiveOfferings()).thenReturn(Optional.empty());
            when(repository.findAllActive()).thenReturn(emptyList);

            // Act
            List<Offering> result = service.listActive();

            // Assert
            assertTrue(result.isEmpty());
            verify(cache).getActiveOfferings();
            verify(repository).findAllActive();
            verify(cache).putActiveOfferings(emptyList);
        }

        @Test
        @DisplayName("Should cache offerings retrieved from repository")
        void shouldCacheOfferingsFromRepository() {
            // Arrange
            Offering offering = new Offering(UUID.randomUUID(), "Spring Boot", "Spring framework course", "BACKEND", BigDecimal.valueOf(55), true);
            List<Offering> offerings = List.of(offering);

            when(cache.getActiveOfferings()).thenReturn(Optional.empty());
            when(repository.findAllActive()).thenReturn(offerings);

            // Act
            service.listActive();

            // Assert
            verify(cache).putActiveOfferings(offerings);
        }

        @Test
        @DisplayName("Should return correct number of active offerings from cache")
        void shouldReturnCorrectCountFromCache() {
            // Arrange
            List<Offering> offerings = List.of(
                new Offering(UUID.randomUUID(), "Offering 1", "Description 1", "BACKEND", BigDecimal.TEN, true),
                new Offering(UUID.randomUUID(), "Offering 2", "Description 2", "FRONTEND", BigDecimal.valueOf(20), true),
                new Offering(UUID.randomUUID(), "Offering 3", "Description 3", "DEVOPS", BigDecimal.valueOf(30), true),
                new Offering(UUID.randomUUID(), "Offering 4", "Description 4", "FULLSTACK", BigDecimal.valueOf(40), true)
            );

            when(cache.getActiveOfferings()).thenReturn(Optional.of(offerings));

            // Act
            List<Offering> result = service.listActive();

            // Assert
            assertEquals(4, result.size());
        }

        @Test
        @DisplayName("Should preserve offering properties when caching and retrieving")
        void shouldPreserveOfferingProperties() {
            // Arrange
            UUID offeringId = UUID.randomUUID();
            String title = "Advanced Spring";
            String description = "Learn advanced Spring concepts";
            String category = "BACKEND";
            BigDecimal price = BigDecimal.valueOf(75.50);
            Offering offering = new Offering(offeringId, title, description, category, price, true);
            List<Offering> offerings = List.of(offering);

            when(cache.getActiveOfferings()).thenReturn(Optional.empty());
            when(repository.findAllActive()).thenReturn(offerings);

            // Act
            List<Offering> result = service.listActive();

            // Assert
            Offering retrieved = result.get(0);
            assertEquals(offeringId, retrieved.id());
            assertEquals(title, retrieved.title());
            assertEquals(description, retrieved.description());
            assertEquals(category, retrieved.category());
            assertEquals(price, retrieved.price());
            assertTrue(retrieved.active());
        }
    }

    @Nested
    @DisplayName("Caching Behavior")
    class CachingBehaviorTests {

        @Test
        @DisplayName("Should not hit repository when cache has data")
        void shouldNotHitRepositoryWhenCacheHasData() {
            // Arrange
            List<Offering> cachedData = List.of(
                new Offering(UUID.randomUUID(), "Cached Offering", "From cache", "BACKEND", BigDecimal.TEN, true)
            );
            when(cache.getActiveOfferings()).thenReturn(Optional.of(cachedData));

            // Act
            service.listActive();
            service.listActive();
            service.listActive();

            // Assert
            verify(cache, times(3)).getActiveOfferings();
            verify(repository, never()).findAllActive();
        }

        @Test
        @DisplayName("Should call repository only once per cache miss")
        void shouldCallRepositoryOncePerCacheMiss() {
            // Arrange
            List<Offering> offerings = List.of(
                new Offering(UUID.randomUUID(), "Offering", "Description", "BACKEND", BigDecimal.TEN, true)
            );

            when(cache.getActiveOfferings()).thenReturn(Optional.empty());
            when(repository.findAllActive()).thenReturn(offerings);

            // Act
            service.listActive();

            // Assert
            verify(repository, times(1)).findAllActive();
            verify(cache, times(1)).putActiveOfferings(offerings);
        }

        @Test
        @DisplayName("Should call cache.putActiveOfferings with exact repository data")
        void shouldCachePutExactRepositoryData() {
            // Arrange
            List<Offering> repositoryData = List.of(
                new Offering(UUID.randomUUID(), "Offering 1", "Desc 1", "BACKEND", BigDecimal.ONE, true),
                new Offering(UUID.randomUUID(), "Offering 2", "Desc 2", "FRONTEND", BigDecimal.TWO, true)
            );

            when(cache.getActiveOfferings()).thenReturn(Optional.empty());
            when(repository.findAllActive()).thenReturn(repositoryData);

            // Act
            service.listActive();

            // Assert
            verify(cache).putActiveOfferings(repositoryData);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle offerings with zero price")
        void shouldHandleZeroPrice() {
            // Arrange
            Offering offering = new Offering(UUID.randomUUID(), "Free Course", "Free offering", "BACKEND", BigDecimal.ZERO, true);
            List<Offering> offerings = List.of(offering);

            when(cache.getActiveOfferings()).thenReturn(Optional.of(offerings));

            // Act
            List<Offering> result = service.listActive();

            // Assert
            assertEquals(BigDecimal.ZERO, result.get(0).price());
        }

        @Test
        @DisplayName("Should handle very large price values")
        void shouldHandleLargePriceValues() {
            // Arrange
            Offering offering = new Offering(UUID.randomUUID(), "Premium Course", "Expensive", "BACKEND", BigDecimal.valueOf(9999.99), true);
            List<Offering> offerings = List.of(offering);

            when(cache.getActiveOfferings()).thenReturn(Optional.of(offerings));

            // Act
            List<Offering> result = service.listActive();

            // Assert
            assertEquals(BigDecimal.valueOf(9999.99), result.get(0).price());
        }

        @Test
        @DisplayName("Should handle offerings with very long titles")
        void shouldHandleLongTitles() {
            // Arrange
            String longTitle = "A".repeat(500);
            Offering offering = new Offering(UUID.randomUUID(), longTitle, "Description", "BACKEND", BigDecimal.TEN, true);
            List<Offering> offerings = List.of(offering);

            when(cache.getActiveOfferings()).thenReturn(Optional.of(offerings));

            // Act
            List<Offering> result = service.listActive();

            // Assert
            assertEquals(longTitle, result.get(0).title());
        }
    }
}
