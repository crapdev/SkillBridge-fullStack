// Refleja el BookingEvent de Java (infrastructure/adapter/out/messaging/BookingEvent.java). Cambiar solo de acuerdo
// con el equipo Spring Boot. El tipo de evento NO viaja en el cuerpo: va en la routing key.
//
// Java lo serializa con `new Jackson2JsonMessageConverter()`, que usa su propio ObjectMapper: los Instant salen como
// segundos epoch con decimales (p. ej. 1795186800.000000000), no como texto ISO. Verificado con spring-amqp 3.2.7 y
// jackson-databind 2.19.2 (las versiones de Spring Boot 3.5.6). Se acepta también ISO por si ese conversor cambia.
export interface BookingEventWire {
  bookingId: string;
  offeringId: string;
  customerId: string;
  scheduledAt: number | string;
  occurredAt: number | string;
}

/** Evento ya validado, con las fechas convertidas. */
export interface BookingEvent {
  bookingId: string;
  offeringId: string;
  customerId: string;
  scheduledAt: Date;
  occurredAt: Date;
}

export const BOOKING_ROUTING_KEYS = {
  CREATED: 'booking.created',
  PAID: 'booking.paid',
  CANCELLED: 'booking.cancelled',
  COMPLETED: 'booking.completed'
} as const;

export type BookingRoutingKey = typeof BOOKING_ROUTING_KEYS[keyof typeof BOOKING_ROUTING_KEYS];
