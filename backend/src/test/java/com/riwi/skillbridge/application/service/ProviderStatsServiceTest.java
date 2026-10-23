package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.in.GetProviderStatsUseCase.ProviderStats;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** SCRUM-187 (HU-22): indicadores del dashboard del proveedor. */
@ExtendWith(MockitoExtension.class)
class ProviderStatsServiceTest {

    @Mock private UserAccountPort users;
    @Mock private OfferingRepositoryPort offerings;
    @Mock private AvailabilitySlotRepositoryPort slots;
    @Mock private BookingRepositoryPort bookings;
    @Mock private CompleteFinishedBookingsUseCase completion;

    @InjectMocks private ProviderStatsService service;

    private final UUID providerId = UUID.randomUUID();
    private final Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
    private final Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

    private Offering offering(String title) {
        return new Offering(UUID.randomUUID(), providerId, title, "Mentoría", "BACKEND", BigDecimal.TEN, true);
    }

    private final Offering withSlots = offering("Java");                          // aprobada con horario libre
    private final Offering onlyReserved = offering("Angular");                    // aprobada, su único horario está tomado
    private final Offering pending = offering("Kubernetes").pendingReview();
    private final Offering rejected = offering("Docker").pendingReview().reject("Falta temario");

    private AvailabilitySlot slot(Offering o, Instant at, boolean reserved) {
        return new AvailabilitySlot(UUID.randomUUID(), o.id(), at, reserved);
    }

    private Booking booking(Offering o, Instant at, BookingStatus status) {
        return new Booking(UUID.randomUUID(), o.id(), UUID.randomUUID(), at, status);
    }

    @BeforeEach
    void setUp() {
        // BUG-05: el completado tiene sus propias pruebas; aquí deja las reservas como vienen
        lenient().when(completion.completeIfFinished(org.mockito.ArgumentMatchers.anyList())).thenAnswer(i -> i.getArgument(0));
        lenient().when(users.findIdByEmail("laura@test.com")).thenReturn(Optional.of(providerId));
        lenient().when(offerings.findByProviderId(providerId)).thenReturn(List.of(withSlots, onlyReserved, pending, rejected));
        lenient().when(slots.findByOfferingId(withSlots.id())).thenReturn(List.of(
                slot(withSlots, tomorrow, false), slot(withSlots, tomorrow.plusSeconds(7200), true),
                slot(withSlots, yesterday, false)));                                  // pasado: no cuenta
        lenient().when(slots.findByOfferingId(onlyReserved.id())).thenReturn(List.of(slot(onlyReserved, tomorrow, true)));
        lenient().when(slots.findByOfferingId(pending.id())).thenReturn(List.of(slot(pending, tomorrow, false)));
        lenient().when(slots.findByOfferingId(rejected.id())).thenReturn(List.of());
        lenient().when(bookings.findByOfferingIds(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(
                booking(withSlots, tomorrow, BookingStatus.CREATED),
                booking(onlyReserved, tomorrow, BookingStatus.CONFIRMED),
                booking(withSlots, yesterday, BookingStatus.COMPLETED),
                booking(withSlots, tomorrow, BookingStatus.CANCELLED),
                booking(onlyReserved, yesterday, BookingStatus.CREATED)));                // pasada sin pagar: no es "próxima"
    }

    @Test
    void countsOfferingsByReviewStatus() {
        var o = service.statsFor("laura@test.com").offerings();

        assertEquals(4, o.total());
        assertEquals(2, o.approved());
        assertEquals(1, o.pendingReview());
        assertEquals(1, o.rejected());
        // Angular está aprobada pero sin horarios libres: los clientes no la ven
        assertEquals(1, o.approvedWithoutFreeSlots());
    }

    @Test
    void countsOnlyUpcomingSlots() {
        var s = service.statsFor("laura@test.com").slots();

        assertEquals(2, s.upcomingFree());       // Java mañana + Kubernetes mañana
        assertEquals(2, s.upcomingReserved());   // Java en 2 h + Angular mañana
    }

    @Test
    void countsReceivedBookingsByStatus() {
        ProviderStats stats = service.statsFor("laura@test.com");
        var b = stats.bookings();

        assertEquals(5, b.total());
        assertEquals(2, b.upcoming());
        assertEquals(2, b.pendingPayment());
        assertEquals(1, b.confirmed());
        assertEquals(1, b.completed());
        assertEquals(1, b.cancelled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void looksForBookingsOfAllItsOfferingsOnly() {
        service.statsFor("laura@test.com");

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(bookings).findByOfferingIds(ids.capture());
        assertEquals(Set.of(withSlots.id(), onlyReserved.id(), pending.id(), rejected.id()), Set.copyOf(ids.getValue()));
    }

    @Test
    void unknownProviderFails() {
        when(users.findIdByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.statsFor("nadie@test.com"));
        verifyNoInteractions(offerings, bookings);
    }
}
