package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.OfferingDraft;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import com.riwi.skillbridge.domain.model.SyllabusTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** SCRUM-178 (HU-21): estado de revisión al crear y editar mentorías. */
@ExtendWith(MockitoExtension.class)
class ProviderOfferingReviewServiceTest {

    private static final String OWNER = "proveedor@email.com";

    @Mock private OfferingRepositoryPort offerings;
    @Mock private AvailabilitySlotRepositoryPort slots;
    @Mock private UserAccountPort users;
    @Mock private OfferingCachePort cache;

    @InjectMocks private ProviderOfferingService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(users.findIdByEmail(OWNER)).thenReturn(Optional.of(ownerId));
        lenient().when(offerings.save(any(Offering.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(offerings.update(any(Offering.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static OfferingDraft draft() {
        return new OfferingDraft("Spring Boot", "APIs REST", "BACKEND", new BigDecimal("90000"),
            List.of(new SyllabusTopic("JPA", "Entidades"), new SyllabusTopic("Seguridad", "JWT")),
            60, OfferingLevel.INTERMEDIO, Set.of(DayOfWeek.MONDAY));
    }

    private void existing(Offering offering) {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
    }

    private Offering approved() {
        return new Offering(offeringId, ownerId, "Java", "Mentoría", "BACKEND", new BigDecimal("85000"), true);
    }

    private Offering sentToUpdate() {
        ArgumentCaptor<Offering> sent = ArgumentCaptor.forClass(Offering.class);
        verify(offerings).update(sent.capture());
        return sent.getValue();
    }

    @Test
    void create_newOfferingStartsPendingReview() {
        service.createOffering(draft(), OWNER);

        ArgumentCaptor<Offering> saved = ArgumentCaptor.forClass(Offering.class);
        verify(offerings).save(saved.capture());
        assertEquals(OfferingReviewStatus.PENDING_REVIEW, saved.getValue().reviewStatus());
        assertNull(saved.getValue().rejectionReason());
    }

    @Test
    void update_approvedStaysApproved() {
        existing(approved());

        service.updateOffering(offeringId, draft(), OWNER);

        assertEquals(OfferingReviewStatus.APPROVED, sentToUpdate().reviewStatus());
    }

    @Test
    void update_pendingStaysPending() {
        existing(approved().pendingReview());

        service.updateOffering(offeringId, draft(), OWNER);

        assertEquals(OfferingReviewStatus.PENDING_REVIEW, sentToUpdate().reviewStatus());
    }

    @Test
    void update_rejectedIsResubmittedForReview() {
        existing(approved().pendingReview().reject("Falta el temario"));

        service.updateOffering(offeringId, draft(), OWNER);

        Offering sent = sentToUpdate();
        assertEquals(OfferingReviewStatus.PENDING_REVIEW, sent.reviewStatus());
        assertNull(sent.rejectionReason());
    }
}
