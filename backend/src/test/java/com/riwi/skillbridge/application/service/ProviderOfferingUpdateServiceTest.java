package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SCRUM-168 (HU-19): pruebas de edición y propiedad de mentorías en la capa de aplicación.
 */
@ExtendWith(MockitoExtension.class)
class ProviderOfferingUpdateServiceTest {

    private static final String OWNER = "proveedor@email.com";
    private static final String OTHER = "otro@email.com";

    @Mock private OfferingRepositoryPort offerings;
    @Mock private AvailabilitySlotRepositoryPort slots;
    @Mock private UserAccountPort users;
    @Mock private OfferingCachePort cache;

    @InjectMocks private ProviderOfferingService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void ownedOffering() {
        Offering offering = new Offering(offeringId, ownerId, "Java", "Mentoría", "BACKEND", new BigDecimal("85000"), true);
        lenient().when(users.findIdByEmail(OWNER)).thenReturn(Optional.of(ownerId));
        lenient().when(users.findIdByEmail(OTHER)).thenReturn(Optional.of(UUID.randomUUID()));
        lenient().when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        lenient().when(offerings.update(any(Offering.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ==========================================
    // EDICIÓN EXITOSA
    // ==========================================

    @Test
    void update_ShouldSaveNewDetails_KeepingOwnerAndStatus() {
        Offering updated = service.updateOffering(offeringId, "  Spring Boot avanzado ", " Seguridad y JPA ",
            " CLOUD ", new BigDecimal("120000"), OWNER);

        ArgumentCaptor<Offering> sent = ArgumentCaptor.forClass(Offering.class);
        verify(offerings).update(sent.capture());
        assertEquals(offeringId, sent.getValue().id());
        assertEquals(ownerId, sent.getValue().providerId());
        assertTrue(sent.getValue().active());
        // Se guardan sin espacios sobrantes
        assertEquals("Spring Boot avanzado", updated.title());
        assertEquals("Seguridad y JPA", updated.description());
        assertEquals("CLOUD", updated.category());
        assertEquals(new BigDecimal("120000"), updated.price());
    }

    @Test
    void update_ShouldEvictPublicCatalogCache() {
        service.updateOffering(offeringId, "Java", "Mentoría", "BACKEND", new BigDecimal("90000"), OWNER);

        // Sin esto el catálogo seguiría mostrando el precio viejo hasta que expire la caché
        verify(cache).evictActiveOfferings();
    }

    // ==========================================
    // PROPIEDAD (403 / 404)
    // ==========================================

    @Test
    void update_ShouldThrowUnauthorized_WhenOfferingBelongsToAnotherProvider() {
        assertThrows(UnauthorizedActionException.class,
            () -> service.updateOffering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, OTHER));

        verify(offerings, never()).update(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @Test
    void update_ShouldThrowNotFound_WhenOfferingDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(offerings.findById(missing)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
            () -> service.updateOffering(missing, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, OWNER));
        verify(offerings, never()).update(any());
    }

    @Test
    void delete_ShouldThrowUnauthorized_WhenOfferingBelongsToAnotherProvider() {
        assertThrows(UnauthorizedActionException.class, () -> service.deleteOffering(offeringId, OTHER));

        verify(offerings, never()).deleteById(any());
        verify(slots, never()).deleteByOfferingId(any());
    }

    // ==========================================
    // VALIDACIONES
    // ==========================================

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-50000"})
    void update_ShouldRejectPriceNotGreaterThanZero(String price) {
        assertThrows(BusinessRuleException.class,
            () -> service.updateOffering(offeringId, "Java", "Mentoría", "BACKEND", new BigDecimal(price), OWNER));
        verify(offerings, never()).update(any());
    }

    @Test
    void update_ShouldRejectMissingPrice() {
        assertThrows(BusinessRuleException.class,
            () -> service.updateOffering(offeringId, "Java", "Mentoría", "BACKEND", null, OWNER));
        verify(offerings, never()).update(any());
    }

    @Test
    void update_ShouldRejectBlankFields() {
        assertThrows(BusinessRuleException.class,
            () -> service.updateOffering(offeringId, "   ", "Mentoría", "BACKEND", BigDecimal.TEN, OWNER));
        assertThrows(BusinessRuleException.class,
            () -> service.updateOffering(offeringId, "Java", "", "BACKEND", BigDecimal.TEN, OWNER));
        assertThrows(BusinessRuleException.class,
            () -> service.updateOffering(offeringId, "Java", "Mentoría", null, BigDecimal.TEN, OWNER));
        verify(offerings, never()).update(any());
    }
}
