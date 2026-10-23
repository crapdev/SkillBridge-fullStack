package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** BUG-05: el proceso de completado pasa a COMPLETED las reservas confirmadas cuya sesión ya terminó. */
class BookingCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T20:00:00Z");

    private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
    private final OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
    private final BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
    private final BookingCompletionService service =
        new BookingCompletionService(bookings, offerings, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    // Mentoría de 90 minutos: la duración la decide la ficha, no un valor fijo
    private final Offering offering = new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java", "Mentoría", "BACKEND",
        BigDecimal.TEN, true, List.of(), 90, OfferingLevel.BASICO, EnumSet.allOf(DayOfWeek.class));

    private Booking booking(Instant scheduledAt, BookingStatus status) {
        return new Booking(UUID.randomUUID(), offering.id(), UUID.randomUUID(), scheduledAt, status);
    }

    @BeforeEach
    void setUp() {
        when(offerings.findById(offering.id())).thenReturn(Optional.of(offering));
        when(bookings.updateStatusIf(any(), any(), any())).thenReturn(true);
    }

    @Test
    void confirmedBookingInThePast_isCompletedAndPublished() {
        Booking past = booking(NOW.minus(2, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
        when(bookings.findConfirmedScheduledBefore(NOW)).thenReturn(List.of(past));

        int completed = service.completeFinished();

        assertEquals(1, completed);
        verify(bookings).updateStatusIf(past.id(), BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
        verify(publisher).bookingCompleted(argThat(b -> b.id().equals(past.id()) && b.status() == BookingStatus.COMPLETED));
    }

    @Test
    void sessionStillInProgress_isNotCompleted() {
        // Empezó hace 1 hora y dura 90 minutos: aún no termina
        Booking inProgress = booking(NOW.minus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
        when(bookings.findConfirmedScheduledBefore(NOW)).thenReturn(List.of(inProgress));

        assertEquals(0, service.completeFinished());
        verify(bookings, never()).updateStatusIf(any(), any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void createdCancelledAndFutureBookings_doNotChange() {
        Instant past = NOW.minus(1, ChronoUnit.DAYS);
        Booking created = booking(past, BookingStatus.CREATED);
        Booking cancelled = booking(past, BookingStatus.CANCELLED);
        Booking future = booking(NOW.plus(1, ChronoUnit.DAYS), BookingStatus.CONFIRMED);

        List<Booking> result = service.completeIfFinished(List.of(created, cancelled, future));

        assertEquals(List.of(created, cancelled, future), result);
        verify(bookings, never()).updateStatusIf(any(), any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void onRead_finishedBookingIsReturnedAsCompleted() {
        Booking past = booking(NOW.minus(1, ChronoUnit.DAYS), BookingStatus.CONFIRMED);
        Booking future = booking(NOW.plus(1, ChronoUnit.DAYS), BookingStatus.CONFIRMED);

        List<Booking> result = service.completeIfFinished(List.of(past, future));

        assertEquals(BookingStatus.COMPLETED, result.get(0).status());
        assertEquals(BookingStatus.CONFIRMED, result.get(1).status());
    }

    @Test
    void alreadyCompletedByAnotherProcess_doesNotPublishTwice() {
        // El proceso programado y una consulta llegan a la vez: la base solo deja que uno haga el cambio
        Booking past = booking(NOW.minus(1, ChronoUnit.DAYS), BookingStatus.CONFIRMED);
        when(bookings.updateStatusIf(past.id(), BookingStatus.CONFIRMED, BookingStatus.COMPLETED)).thenReturn(false);

        List<Booking> result = service.completeIfFinished(List.of(past));

        assertEquals(BookingStatus.COMPLETED, result.get(0).status());
        verifyNoInteractions(publisher);
    }

    @Test
    void offeringDurationIsLookedUpOncePerOffering() {
        Instant past = NOW.minus(1, ChronoUnit.DAYS);

        service.completeIfFinished(List.of(booking(past, BookingStatus.CONFIRMED), booking(past, BookingStatus.CONFIRMED)));

        verify(offerings, times(1)).findById(offering.id());
        verify(publisher, times(2)).bookingCompleted(any());
    }
}
