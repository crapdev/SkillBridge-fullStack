package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ReviewOfferingsUseCase.OfferingForReview;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AccountStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** SCRUM-179 (HU-21): el administrador aprueba o rechaza mentorías pendientes. */
@ExtendWith(MockitoExtension.class)
class OfferingReviewServiceTest {

    @Mock private OfferingRepositoryPort offerings;
    @Mock private UserRepositoryPort users;
    @Mock private OfferingCachePort cache;

    @InjectMocks private OfferingReviewService service;

    private final UUID providerId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(users.findById(providerId)).thenReturn(Optional.of(new UserAccount(providerId, "Laura Gómez",
                "laura@test.com", "hash", Role.PROVIDER, AccountStatus.ACTIVE)));
        lenient().when(offerings.update(any(Offering.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Offering pending() {
        return new Offering(offeringId, providerId, "Kubernetes", "Pods", "CLOUD", BigDecimal.TEN, true).pendingReview();
    }

    @Test
    void listPending_includesWhoProposesIt() {
        when(offerings.findByReviewStatus(OfferingReviewStatus.PENDING_REVIEW)).thenReturn(List.of(pending()));

        OfferingForReview item = service.listPending().get(0);

        assertEquals(offeringId, item.offering().id());
        assertEquals("Laura Gómez", item.providerName());
        assertEquals("laura@test.com", item.providerEmail());
    }

    @Test
    void approve_savesApprovedAndRefreshesTheCatalogCache() {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(pending()));

        OfferingForReview result = service.approve(offeringId);

        assertEquals(OfferingReviewStatus.APPROVED, result.offering().reviewStatus());
        verify(offerings).update(argThat(Offering::isApproved));
        verify(cache).evictActiveOfferings();
    }

    @Test
    void reject_savesTheReason() {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(pending()));

        OfferingForReview result = service.reject(offeringId, "El temario no corresponde a la categoría");

        assertEquals(OfferingReviewStatus.REJECTED, result.offering().reviewStatus());
        assertEquals("El temario no corresponde a la categoría", result.offering().rejectionReason());
        verify(cache).evictActiveOfferings();
    }

    @Test
    void reviewingAnAlreadyApprovedOfferingFails() {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(pending().approve()));

        assertThrows(BusinessRuleException.class, () -> service.approve(offeringId));
        verify(offerings, never()).update(any());
        verifyNoInteractions(cache);
    }

    @Test
    void unknownOfferingFails() {
        when(offerings.findById(offeringId)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.reject(offeringId, null));
    }
}
