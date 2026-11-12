export const BOOKING_EVENTS_EXCHANGE = 'booking.events';
export const BOOKING_EVENTS_EXCHANGE_TYPE = 'topic';
export const BOOKING_EMAILS_QUEUE = 'booking.emails.queue';
export const BOOKING_EMAILS_DLX = 'booking.emails.dlx';
export const BOOKING_EMAILS_DLQ = 'booking.emails.dlq';
export const BOOKING_EMAIL_ROUTING_KEYS = [
  'booking.created',
  'booking.paid',
  'booking.cancelled',
  'booking.completed'
] as const;
