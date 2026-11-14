// Nombres compartidos con el backend Java (RabbitConfiguration). Cambiarlos rompe la integración.
export const BOOKING_EVENTS_EXCHANGE = 'booking.events';
export const BOOKING_EVENTS_EXCHANGE_TYPE = 'topic';

// Topología propia del email-service: no toca booking.notifications.queue ni la booking.dlx de Java
export const BOOKING_EMAILS_QUEUE = 'booking.emails.queue';
export const BOOKING_EMAILS_DLX = 'booking.emails.dlx';
export const BOOKING_EMAILS_DLX_TYPE = 'fanout';
export const BOOKING_EMAILS_DLQ = 'booking.emails.dlq';
export const BOOKING_EMAILS_RETRY_QUEUE = 'booking.emails.retry.queue';

export const BOOKING_EMAIL_ROUTING_KEYS = [
  'booking.created',
  'booking.paid',
  'booking.cancelled',
  'booking.completed'
] as const;

// Cabeceras que añade el email-service al reprogramar un mensaje en la cola de reintentos
export const HEADER_ATTEMPT = 'x-email-attempt';
export const HEADER_ORIGINAL_ROUTING_KEY = 'x-original-routing-key';
export const HEADER_LAST_ERROR = 'x-email-last-error';
