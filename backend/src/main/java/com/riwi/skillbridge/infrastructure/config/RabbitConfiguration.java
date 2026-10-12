package com.riwi.skillbridge.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String BOOKING_CREATED_KEY = "booking.created";
    public static final String BOOKING_CANCELLED_KEY = "booking.cancelled";
    public static final String BOOKING_PAID_KEY = "booking.paid";
    public static final String PAYMENT_FAILED_KEY = "booking.payment-failed";

    // Una sola cola recibe todos los eventos de reservas (booking.*) para generar las notificaciones
    public static final String NOTIFICATIONS_QUEUE = "booking.notifications.queue";
    public static final String ALL_BOOKING_EVENTS = "booking.*";

    public static final String DLX = "booking.dlx";
    public static final String DLQ = "booking.notifications.dlq";
    public static final String DLQ_KEY = "booking.notifications.failed";

    @Bean TopicExchange bookingExchange() { return new TopicExchange(BOOKING_EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    // Los mensajes que fallan después de los reintentos (application.yml) pasan a la DLQ para diagnosticarlos
    @Bean
    Queue notificationsQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_KEY)
                .build();
    }

    @Bean Queue notificationsDeadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean
    Binding notificationsBinding(Queue notificationsQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(notificationsQueue).to(bookingExchange).with(ALL_BOOKING_EVENTS);
    }

    @Bean
    Binding notificationsDlqBinding(Queue notificationsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationsDeadLetterQueue).to(deadLetterExchange).with(DLQ_KEY);
    }

    @Bean MessageConverter jacksonMessageConverter() { return new Jackson2JsonMessageConverter(); }
}
