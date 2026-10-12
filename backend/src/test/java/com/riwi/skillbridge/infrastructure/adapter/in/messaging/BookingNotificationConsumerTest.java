package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.RecordBookingNotificationUseCase;
import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.BookingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingNotificationConsumerTest {

    private final RecordBookingNotificationUseCase useCase = mock(RecordBookingNotificationUseCase.class);
    private final BookingNotificationConsumer consumer = new BookingNotificationConsumer(useCase);
    private final BookingEvent event = new BookingEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Instant.now().plusSeconds(86400), Instant.now());

    @ParameterizedTest
    @CsvSource({
            "booking.created, BOOKING_CREATED",
            "booking.cancelled, BOOKING_CANCELLED",
            "booking.paid, BOOKING_PAID"
    })
    void routingKeyDecidesTheNotificationType(String routingKey, NotificationType expected) {
        consumer.onBookingEvent(event, routingKey);

        verify(useCase).record(expected, event.bookingId());
    }

    @ParameterizedTest
    @CsvSource({"booking.payment-failed", "booking.rescheduled"})
    void eventsWithoutNotificationAreIgnored(String routingKey) {
        consumer.onBookingEvent(event, routingKey);

        verifyNoInteractions(useCase);
    }

    @Test
    void failuresPropagateSoRabbitCanRetryAndDeadLetter() {
        doThrow(new IllegalStateException("BD caída")).when(useCase).record(any(), any());

        assertThrows(IllegalStateException.class, () -> consumer.onBookingEvent(event, "booking.created"));
    }
}
