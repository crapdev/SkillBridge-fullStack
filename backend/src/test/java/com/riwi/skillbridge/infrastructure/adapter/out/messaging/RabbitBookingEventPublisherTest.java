package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RabbitBookingEventPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitBookingEventPublisher publisher = new RabbitBookingEventPublisher(rabbitTemplate);
    private final Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Instant.now().plusSeconds(86400), BookingStatus.CREATED);

    @Test
    void eachEventUsesItsRoutingKey() {
        publisher.bookingCreated(booking);
        publisher.bookingCancelled(booking);
        publisher.bookingPaid(booking);
        publisher.paymentFailed(booking);

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfiguration.BOOKING_EXCHANGE), eq("booking.created"), any(BookingEvent.class));
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfiguration.BOOKING_EXCHANGE), eq("booking.cancelled"), any(BookingEvent.class));
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfiguration.BOOKING_EXCHANGE), eq("booking.paid"), any(BookingEvent.class));
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfiguration.BOOKING_EXCHANGE), eq("booking.payment-failed"), any(BookingEvent.class));
    }

    @Test
    void eventCarriesOnlyIdsAndDates() {
        publisher.bookingPaid(booking);

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfiguration.BOOKING_EXCHANGE), eq("booking.paid"), sent.capture());
        BookingEvent event = (BookingEvent) sent.getValue();
        assertEquals(booking.id(), event.bookingId());
        assertEquals(booking.offeringId(), event.offeringId());
        assertEquals(booking.customerId(), event.customerId());
        assertEquals(booking.scheduledAt(), event.scheduledAt());
    }
}
