package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.RecordBookingNotificationUseCase;
import com.riwi.skillbridge.domain.model.NotificationType;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.BookingEvent;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Convierte los eventos de reservas en notificaciones. Si guardar falla, la excepción se propaga:
 * Spring reintenta (application.yml) y después el mensaje pasa a la DLQ para diagnosticarlo.
 */
@Component
public class BookingNotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);

    // El tipo de evento viaja en la routing key. El pago fallido no genera notificación: solo le
    // importa al cliente, que ya ve el error en la pantalla de pago
    private static final Map<String, NotificationType> TYPES = Map.of(
            RabbitConfiguration.BOOKING_CREATED_KEY, NotificationType.BOOKING_CREATED,
            RabbitConfiguration.BOOKING_CANCELLED_KEY, NotificationType.BOOKING_CANCELLED,
            RabbitConfiguration.BOOKING_PAID_KEY, NotificationType.BOOKING_PAID);

    private final RecordBookingNotificationUseCase useCase;

    public BookingNotificationConsumer(RecordBookingNotificationUseCase useCase) {
        this.useCase = useCase;
    }

    @RabbitListener(queues = RabbitConfiguration.NOTIFICATIONS_QUEUE)
    public void onBookingEvent(BookingEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        NotificationType type = TYPES.get(routingKey);
        if (type == null) {
            // La cola recibe todos los eventos booking.*; los que no notifican se confirman y se descartan
            log.debug("Evento de reserva sin notificación asociada: routingKey={}, bookingId={}", routingKey, event.bookingId());
            return;
        }
        // Solo ids en el log: nada de emails, nombres ni montos
        log.info("Evento de reserva recibido: tipo={}, bookingId={}", type, event.bookingId());
        useCase.record(type, event.bookingId());
    }
}
