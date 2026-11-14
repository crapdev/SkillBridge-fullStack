import { BookingEmailError } from './booking-email.errors';
import { BookingEvent, BookingRoutingKey } from './booking-event.types';
import { BOOKING_EMAIL_ROUTING_KEYS } from './booking-events.constants';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isBookingEmailRoutingKey(key: string): key is BookingRoutingKey {
  return (BOOKING_EMAIL_ROUTING_KEYS as readonly string[]).includes(key);
}

/**
 * Convierte el cuerpo del mensaje en un BookingEvent validado. Todo fallo es permanente: el mismo mensaje
 * nunca va a ser válido, así que reintentarlo solo retrasaría su llegada a la DLQ.
 */
export function parseBookingEvent(content: Buffer): BookingEvent {
  let raw: unknown;
  try {
    raw = JSON.parse(content.toString('utf8'));
  } catch {
    throw BookingEmailError.permanent('invalid_json');
  }
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    throw BookingEmailError.permanent('invalid_payload', 'el cuerpo no es un objeto JSON');
  }
  const body = raw as Record<string, unknown>;
  return {
    bookingId: uuid(body, 'bookingId'),
    offeringId: uuid(body, 'offeringId'),
    customerId: uuid(body, 'customerId'),
    scheduledAt: instant(body, 'scheduledAt'),
    occurredAt: instant(body, 'occurredAt')
  };
}

function uuid(body: Record<string, unknown>, field: string): string {
  const value = body[field];
  if (typeof value !== 'string' || !UUID_RE.test(value)) {
    throw BookingEmailError.permanent('invalid_payload', `${field} no es un UUID`);
  }
  return value.toLowerCase();
}

/** Instant de Jackson: número de segundos epoch (con decimales) o, alternativamente, texto ISO-8601. */
function instant(body: Record<string, unknown>, field: string): Date {
  const value = body[field];
  let date: Date | undefined;
  if (typeof value === 'number' && Number.isFinite(value)) {
    date = new Date(Math.round(value * 1000));
  } else if (typeof value === 'string' && value.trim() !== '' && Number.isNaN(Number(value))) {
    date = new Date(value);
  }
  if (!date || Number.isNaN(date.getTime())) {
    throw BookingEmailError.permanent('invalid_payload', `${field} no es una fecha válida`);
  }
  return date;
}
