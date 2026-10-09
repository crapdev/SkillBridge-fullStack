package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.*;
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
        AvailabilitySlot availabilitySlot = new AvailabilitySlot(UUID.randomUUID(), offeringId, scheduledAt, false);

        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(slots.findByOfferingIdAndScheduledAt(offeringId, scheduledAt)).thenReturn(Optional.of(availabilitySlot));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = new BookingService(bookings, offerings, users, publisher, slots);
        Booking result = service.create(offeringId, Instant.now().plusSeconds(3600), "user@example.com");

        assertEquals(offeringId, result.offeringId());
        assertEquals(userId, result.customerId());
        verify(bookings).save(any(Booking.class));
        verify(publisher).bookingCreated(result);
        verify(slots).save(any(AvailabilitySlot.class));
    }
}
