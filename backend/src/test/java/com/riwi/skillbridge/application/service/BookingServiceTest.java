package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingServiceTest {

    @Test
    void shouldPersistAndPublishBookingCreated() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);

        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        Offering offering = new Offering(offeringId, providerId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        AvailabilitySlot availableSlot = new AvailabilitySlot(UUID.randomUUID(), offeringId, scheduledAt, false);

        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(slots.findByOfferingIdAndScheduledAt(offeringId, scheduledAt)).thenReturn(Optional.of(availableSlot));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = new BookingService(bookings, offerings, users, publisher, slots);
        Booking result = service.create(offeringId, scheduledAt, "user@example.com");

        assertEquals(offeringId, result.offeringId());
        assertEquals(userId, result.customerId());
        verify(bookings).save(any(Booking.class));
        verify(publisher).bookingCreated(result);
        verify(slots).save(any(AvailabilitySlot.class));
    }

    // HU-09: Tests de cancelación
    @Test
    void shouldCancelBookingSuccessfully() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, UUID.randomUUID(), userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED);
        
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users, mock(BookingEventPublisherPort.class));
        Booking result = service.cancelBooking(bookingId, "user@example.com");

        assertEquals(com.riwi.skillbridge.domain.model.BookingStatus.CANCELLED, result.status());
        verify(bookings).save(any(Booking.class));
    }

    @Test
    void shouldThrowWhenCancelingAnotherUsersBooking() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        UUID bookingId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();
        Booking existing = new Booking(bookingId, UUID.randomUUID(), ownerId, Instant.now().plusSeconds(3600), com.riwi.skillbridge.domain.model.BookingStatus.CREATED);
        
        when(users.findIdByEmail("hacker@example.com")).thenReturn(Optional.of(hackerId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users, mock(BookingEventPublisherPort.class));
        
        org.junit.jupiter.api.Assertions.assertThrows(
            com.riwi.skillbridge.domain.exception.UnauthorizedActionException.class, 
            () -> service.cancelBooking(bookingId, "hacker@example.com")
        );
        verify(bookings, never()).save(any());
    }

    @Test
    void shouldThrowWhenCancelingOutOfTimeWindow() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        // Reserva en 12 horas (dentro del límite prohibido de 24 horas)
        Instant scheduledAt = Instant.now().plus(12, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, UUID.randomUUID(), userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED);
        
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users, mock(BookingEventPublisherPort.class));
        
        org.junit.jupiter.api.Assertions.assertThrows(
            com.riwi.skillbridge.domain.exception.BusinessRuleException.class, 
            () -> service.cancelBooking(bookingId, "user@example.com")
        );
        verify(bookings, never()).save(any());
    }
}
