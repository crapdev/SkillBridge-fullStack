package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class RabbitBookingEventPublisher implements BookingEventPublisherPort {
    private final RabbitTemplate rabbitTemplate;

    public RabbitBookingEventPublisher(RabbitTemplate rabbitTemplate) { this.rabbitTemplate = rabbitTemplate; }

    @Override
    public void bookingCreated(Booking booking) { publish(RabbitConfiguration.BOOKING_CREATED_KEY, booking); }

    @Override
    public void bookingCancelled(Booking booking) { publish(RabbitConfiguration.BOOKING_CANCELLED_KEY, booking); }

    @Override
    public void bookingPaid(Booking booking) { publish(RabbitConfiguration.BOOKING_PAID_KEY, booking); }

    @Override
    public void paymentFailed(Booking booking) { publish(RabbitConfiguration.PAYMENT_FAILED_KEY, booking); }

    @Override
    public void bookingCompleted(Booking booking) { publish(RabbitConfiguration.BOOKING_COMPLETED_KEY, booking); }

    // Todos los eventos comparten el mismo contenido; la routing key indica qué pasó
    private void publish(String routingKey, Booking booking) {
        BookingEvent event = new BookingEvent(
                booking.id(), booking.offeringId(), booking.customerId(), booking.scheduledAt(), Instant.now());
        rabbitTemplate.convertAndSend(RabbitConfiguration.BOOKING_EXCHANGE, routingKey, event);
    }
}
