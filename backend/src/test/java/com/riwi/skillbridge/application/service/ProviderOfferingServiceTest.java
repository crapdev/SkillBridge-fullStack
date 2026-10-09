package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderOfferingServiceTest {

    private static final String EMAIL = "proveedor@email.com";

    @Mock private OfferingRepositoryPort offerings;
    @Mock private AvailabilitySlotRepositoryPort slots;
    @Mock private UserAccountPort users;

    @InjectMocks private ProviderOfferingService service;

    private final UUID providerId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();
    private final Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void ownedOffering() {
        Offering offering = new Offering(offeringId, providerId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        lenient().when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(providerId));
        lenient().when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
    }

    // ==========================================
    // PUBLICAR HORARIOS
    // ==========================================

    @Test
    void addSlot_ShouldSaveFreeSlot_WhenDateIsFutureAndNotDuplicated() {
        when(slots.existsByOfferingIdAndScheduledAt(offeringId, tomorrow)).thenReturn(false);
        when(slots.save(any(AvailabilitySlot.class))).thenAnswer(i -> i.getArgument(0));

        AvailabilitySlot slot = service.addAvailabilitySlot(offeringId, tomorrow, EMAIL);

        assertEquals(offeringId, slot.offeringId());
        assertEquals(tomorrow, slot.scheduledAt());
        assertFalse(slot.reserved());
    }

    @Test
    void addSlot_ShouldThrow_WhenDateIsInThePast() {
        assertThrows(BusinessRuleException.class,
            () -> service.addAvailabilitySlot(offeringId, Instant.now().minus(1, ChronoUnit.HOURS), EMAIL));
        verify(slots, never()).save(any());
    }

    @Test
    void addSlot_ShouldThrow_WhenSlotAlreadyExists() {
        when(slots.existsByOfferingIdAndScheduledAt(offeringId, tomorrow)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.addAvailabilitySlot(offeringId, tomorrow, EMAIL));
        verify(slots, never()).save(any());
    }

    @Test
    void addSlot_ShouldThrow_WhenOfferingBelongsToAnotherProvider() {
        when(users.findIdByEmail("otro@email.com")).thenReturn(Optional.of(UUID.randomUUID()));

        assertThrows(BusinessRuleException.class,
            () -> service.addAvailabilitySlot(offeringId, tomorrow, "otro@email.com"));
        verify(slots, never()).save(any());
    }

    // ==========================================
    // CONSULTAR HORARIOS
    // ==========================================

    @Test
    void listAvailableSlots_ShouldHidePastSlots() {
        AvailabilitySlot past = new AvailabilitySlot(UUID.randomUUID(), offeringId, Instant.now().minus(1, ChronoUnit.DAYS), false);
        AvailabilitySlot future = new AvailabilitySlot(UUID.randomUUID(), offeringId, tomorrow, false);
        when(slots.findAvailableByOfferingId(offeringId)).thenReturn(List.of(past, future));

        assertEquals(List.of(future), service.listAvailableSlots(offeringId));
    }

    @Test
    void listOwnedSlots_ShouldIncludeReservedUpcomingSlots() {
        AvailabilitySlot reserved = new AvailabilitySlot(UUID.randomUUID(), offeringId, tomorrow, true);
        when(slots.findByOfferingId(offeringId)).thenReturn(List.of(reserved));

        assertEquals(List.of(reserved), service.listOwnedSlots(offeringId, EMAIL));
    }

    // ==========================================
    // ELIMINAR HORARIOS Y MENTORÍAS
    // ==========================================

    @Test
    void deleteSlot_ShouldDelete_WhenSlotIsFree() {
        UUID slotId = UUID.randomUUID();
        when(slots.findById(slotId)).thenReturn(Optional.of(new AvailabilitySlot(slotId, offeringId, tomorrow, false)));

        service.deleteAvailabilitySlot(offeringId, slotId, EMAIL);

        verify(slots).deleteById(slotId);
    }

    @Test
    void deleteSlot_ShouldThrow_WhenSlotIsReserved() {
        UUID slotId = UUID.randomUUID();
        when(slots.findById(slotId)).thenReturn(Optional.of(new AvailabilitySlot(slotId, offeringId, tomorrow, true)));

        assertThrows(BusinessRuleException.class, () -> service.deleteAvailabilitySlot(offeringId, slotId, EMAIL));
        verify(slots, never()).deleteById(any());
    }

    @Test
    void deleteSlot_ShouldThrow_WhenSlotBelongsToAnotherOffering() {
        UUID slotId = UUID.randomUUID();
        when(slots.findById(slotId)).thenReturn(Optional.of(new AvailabilitySlot(slotId, UUID.randomUUID(), tomorrow, false)));

        assertThrows(DomainNotFoundException.class, () -> service.deleteAvailabilitySlot(offeringId, slotId, EMAIL));
        verify(slots, never()).deleteById(any());
    }

    @Test
    void deleteOffering_ShouldRemoveFreeSlotsFirst() {
        when(slots.findByOfferingId(offeringId)).thenReturn(List.of(new AvailabilitySlot(UUID.randomUUID(), offeringId, tomorrow, false)));

        service.deleteOffering(offeringId, EMAIL);

        verify(slots).deleteByOfferingId(offeringId);
        verify(offerings).deleteById(offeringId);
    }

    @Test
    void deleteOffering_ShouldThrow_WhenItHasReservedSlots() {
        when(slots.findByOfferingId(offeringId)).thenReturn(List.of(new AvailabilitySlot(UUID.randomUUID(), offeringId, tomorrow, true)));

        assertThrows(BusinessRuleException.class, () -> service.deleteOffering(offeringId, EMAIL));
        verify(offerings, never()).deleteById(any());
    }
}
