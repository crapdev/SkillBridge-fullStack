export const BOOKING_EVENTS_EXCHANGE = 'booking.events';
export const BOOKING_EVENTS_EXCHANGE_TYPE = 'topic';
export const BOOKING_EMAILS_QUEUE = 'booking.emails.queue';
export const BOOKING_EMAILS_DLX = 'booking.emails.dlx';
export const BOOKING_EMAILS_DLQ = 'booking.emails.dlq';
export const BOOKING_EMAILS_DLQ_KEY = 'booking.emails.failed';
// Se enlazan una a una (no booking.*) para no recibir booking.payment-failed
export const BOOKING_EMAIL_ROUTING_KEYS = [
  'booking.created',
  'booking.paid',
  'booking.cancelled',
  'booking.completed'
] as const;

// 3 intentos en total dentro del proceso: el 2.º a los 2 s y el 3.º a los 10 s; después, a la DLQ
export const RETRY_DELAYS_MS = [2_000, 10_000];
