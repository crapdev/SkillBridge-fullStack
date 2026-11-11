package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
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
import static org.mockito.ArgumentMatchers.argThat;
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

        BookingService service = new BookingService(bookings, offerings, users, publisher, slots, mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));
        Booking result = service.create(offeringId, scheduledAt, "user@example.com");

        assertEquals(offeringId, result.offeringId());
        assertEquals(userId, result.customerId());
        verify(bookings).save(any(Booking.class));
        verify(publisher).bookingCreated(result);
        verify(slots).save(any(AvailabilitySlot.class));
    }

    // HU-21: una mentoría pendiente o rechazada no está en el catálogo y tampoco se puede reservar por la API
    @Test
    void shouldRejectBookingOfOfferingNotApprovedByAdmin() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);
        UUID offeringId = UUID.randomUUID();
        Offering pending = new Offering(offeringId, UUID.randomUUID(), "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true)
                .pendingReview();
        when(offerings.findById(offeringId)).thenReturn(Optional.of(pending));

        BookingService service = new BookingService(bookings, offerings, mock(UserAccountPort.class),
                mock(BookingEventPublisherPort.class), slots, mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));

        org.junit.jupiter.api.Assertions.assertThrows(com.riwi.skillbridge.domain.exception.BusinessRuleException.class,
                () -> service.create(offeringId, Instant.now().plusSeconds(3600), "user@example.com"));
        verify(bookings, never()).save(any());
        verify(slots, never()).save(any());
    }

    // HU-09: Tests de cancelación
    @Test
    void shouldCancelBookingSuccessfully() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, UUID.randomUUID(), userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED, new java.math.BigDecimal("85000"));
        
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users, publisher, mock(AvailabilitySlotRepositoryPort.class), mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));
        Booking result = service.cancelBooking(bookingId, "user@example.com");

        assertEquals(com.riwi.skillbridge.domain.model.BookingStatus.CANCELLED, result.status());
        verify(bookings).save(any(Booking.class));
        // HU-11: la cancelación avisa para generar las notificaciones
        verify(publisher).bookingCancelled(result);
    }

    @Test
    void shouldThrowWhenCancelingAnotherUsersBooking() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        
        UUID bookingId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();
        Booking existing = new Booking(bookingId, UUID.randomUUID(), ownerId, Instant.now().plusSeconds(3600), com.riwi.skillbridge.domain.model.BookingStatus.CREATED, new java.math.BigDecimal("85000"));
        
        when(users.findIdByEmail("hacker@example.com")).thenReturn(Optional.of(hackerId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users, mock(BookingEventPublisherPort.class), mock(AvailabilitySlotRepositoryPort.class), mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));
        
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
        Booking existing = new Booking(bookingId, UUID.randomUUID(), userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED, new java.math.BigDecimal("85000"));
        
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));

        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users, publisher, mock(AvailabilitySlotRepositoryPort.class), mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));

        org.junit.jupiter.api.Assertions.assertThrows(
            com.riwi.skillbridge.domain.exception.BusinessRuleException.class,
            () -> service.cancelBooking(bookingId, "user@example.com")
        );
        verify(bookings, never()).save(any());
        // HU-11: una cancelación rechazada no genera notificaciones
        verifyNoInteractions(publisher);
    }

    // BUG-06: cancelar una reserva debe liberar la franja para que vuelva a estar disponible
    @Test
    void shouldReleaseSlotWhenBookingIsCancelled() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);

        UUID bookingId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, offeringId, userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED, new java.math.BigDecimal("85000"));
        AvailabilitySlot reservedSlot = new AvailabilitySlot(slotId, offeringId, scheduledAt, true);

        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(slots.findByOfferingIdAndScheduledAt(offeringId, scheduledAt)).thenReturn(Optional.of(reservedSlot));
        OfferingCachePort cache = mock(OfferingCachePort.class);

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users,
                mock(BookingEventPublisherPort.class), slots, mock(CompleteFinishedBookingsUseCase.class), cache);
        service.cancelBooking(bookingId, "user@example.com");

        verify(slots).save(new AvailabilitySlot(slotId, offeringId, scheduledAt, false));
        // la mentoría puede volver a aparecer en el catálogo (solo se listan las que tienen un horario libre)
        verify(cache).evictActiveOfferings();
    }

    // BUG-06: si la franja ya no existe (el proveedor la borró), la cancelación igual se completa
    @Test
    void shouldCancelBookingEvenWhenSlotNoLongerExists() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);

        UUID bookingId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, offeringId, userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED, new java.math.BigDecimal("85000"));

        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(slots.findByOfferingIdAndScheduledAt(offeringId, scheduledAt)).thenReturn(Optional.empty());

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users,
                mock(BookingEventPublisherPort.class), slots, mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));
        Booking result = service.cancelBooking(bookingId, "user@example.com");

        assertEquals(com.riwi.skillbridge.domain.model.BookingStatus.CANCELLED, result.status());
        verify(slots, never()).save(any());
    }

    // BUG-06: una cancelación rechazada (menos de 24 horas) no toca la franja
    @Test
    void shouldNotReleaseSlotWhenCancellationIsRejectedByTimeWindow() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);

        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(12, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, UUID.randomUUID(), userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED, new java.math.BigDecimal("85000"));

        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users,
                mock(BookingEventPublisherPort.class), slots, mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));

        org.junit.jupiter.api.Assertions.assertThrows(com.riwi.skillbridge.domain.exception.BusinessRuleException.class,
                () -> service.cancelBooking(bookingId, "user@example.com"));
        verifyNoInteractions(slots);
    }

    // BUG-06: una cancelación de una reserva ajena no toca la franja
    @Test
    void shouldNotReleaseSlotWhenCancellingAnotherUsersBooking() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);

        UUID bookingId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, UUID.randomUUID(), UUID.randomUUID(), scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CREATED, new java.math.BigDecimal("85000"));

        when(users.findIdByEmail("hacker@example.com")).thenReturn(Optional.of(hackerId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users,
                mock(BookingEventPublisherPort.class), slots, mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));

        org.junit.jupiter.api.Assertions.assertThrows(com.riwi.skillbridge.domain.exception.UnauthorizedActionException.class,
                () -> service.cancelBooking(bookingId, "hacker@example.com"));
        verifyNoInteractions(slots);
    }

    // BUG-06: cancelar una reserva ya cancelada no toca la franja
    @Test
    void shouldNotReleaseSlotWhenBookingIsAlreadyCancelled() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);

        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS);
        Booking existing = new Booking(bookingId, UUID.randomUUID(), userId, scheduledAt, com.riwi.skillbridge.domain.model.BookingStatus.CANCELLED, new java.math.BigDecimal("85000"));

        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(existing));

        BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users,
                mock(BookingEventPublisherPort.class), slots, mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));

        org.junit.jupiter.api.Assertions.assertThrows(com.riwi.skillbridge.domain.exception.BusinessRuleException.class,
                () -> service.cancelBooking(bookingId, "user@example.com"));
        verifyNoInteractions(slots);
    }

    // BUG-07: arma el servicio para crear una reserva sobre una franja libre de esa mentoría
    private BookingService serviceForCreate(BookingRepositoryPort bookings, UUID offeringId, UUID customerId,
                                            String email, Instant scheduledAt, AvailabilitySlotRepositoryPort slots) {
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        Offering offering = new Offering(offeringId, UUID.randomUUID(), "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        when(users.findIdByEmail(email)).thenReturn(Optional.of(customerId));
        when(slots.findByOfferingIdAndScheduledAt(offeringId, scheduledAt))
                .thenReturn(Optional.of(new AvailabilitySlot(UUID.randomUUID(), offeringId, scheduledAt, false)));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        return new BookingService(bookings, offerings, users, mock(BookingEventPublisherPort.class), slots,
                mock(CompleteFinishedBookingsUseCase.class), mock(OfferingCachePort.class));
    }

    // BUG-07: una reserva cancelada a esa hora no impide que otro cliente reserve la franja liberada
    @Test
    void shouldAllowBookingASlotWhoseOnlyBookingWasCancelled() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);
        UUID offeringId = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        // la única reserva a esa hora está CANCELLED: el repositorio no la cuenta como activa
        when(bookings.existsActiveByOfferingIdAndScheduledAt(offeringId, scheduledAt)).thenReturn(false);
        when(bookings.existsActiveByCustomerIdAndScheduledAt(customerB, scheduledAt)).thenReturn(false);

        BookingService service = serviceForCreate(bookings, offeringId, customerB, "b@example.com", scheduledAt, slots);
        Booking result = service.create(offeringId, scheduledAt, "b@example.com");

        assertEquals(customerB, result.customerId());
        verify(bookings).existsActiveByOfferingIdAndScheduledAt(offeringId, scheduledAt);
        verify(bookings).existsActiveByCustomerIdAndScheduledAt(customerB, scheduledAt);
        verify(slots).save(argThat(slot -> slot.reserved() && slot.scheduledAt().equals(scheduledAt)));
    }

    // BUG-07: una reserva activa de otro cliente sigue bloqueando la franja
    @Test
    void shouldRejectBookingWhenAnotherActiveBookingExistsAtThatTime() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);
        UUID offeringId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        when(bookings.existsActiveByOfferingIdAndScheduledAt(offeringId, scheduledAt)).thenReturn(true);

        BookingService service = serviceForCreate(bookings, offeringId, UUID.randomUUID(), "b@example.com", scheduledAt, slots);

        BusinessRuleException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class,
                () -> service.create(offeringId, scheduledAt, "b@example.com"));
        assertEquals("Este horario ya fue reservado por otro usuario", ex.getMessage());
        verify(bookings, never()).save(any());
        verify(slots, never()).save(any());
    }

    // BUG-07: el cliente con otra reserva activa a esa hora sigue sin poder reservar
    @Test
    void shouldRejectBookingWhenCustomerHasAnotherActiveBookingAtThatTime() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        AvailabilitySlotRepositoryPort slots = mock(AvailabilitySlotRepositoryPort.class);
        UUID offeringId = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS);
        when(bookings.existsActiveByCustomerIdAndScheduledAt(customerA, scheduledAt)).thenReturn(true);

        BookingService service = serviceForCreate(bookings, offeringId, customerA, "a@example.com", scheduledAt, slots);

        BusinessRuleException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class,
                () -> service.create(offeringId, scheduledAt, "a@example.com"));
        assertEquals("Ya tienes otra mentoría agendada en este mismo horario", ex.getMessage());
        verify(bookings, never()).save(any());
        verify(slots, never()).save(any());
    }
}
