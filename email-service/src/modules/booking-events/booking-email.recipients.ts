import { BookingRoutingKey } from './booking-event.types';
import { BookingEmailContext } from './context/booking-context.port';
import { RecipientRole } from './templates/booking-email.templates';

export interface BookingEmailRecipient {
  role: RecipientRole;
  name: string;
  email: string;
}

/**
 * Quién recibe correo por cada evento (acordado con el equipo; ver README):
 * los cuatro eventos avisan al cliente y al proveedor. En booking.completed el cliente recibe la invitación a
 * calificar (HU-27). La campana de Java sigue avisando solo al proveedor: son canales independientes.
 */
const MATRIX: Record<BookingRoutingKey, readonly RecipientRole[]> = {
  'booking.created': ['customer', 'provider'],
  'booking.paid': ['customer', 'provider'],
  'booking.cancelled': ['customer', 'provider'],
  'booking.completed': ['customer', 'provider']
};

/** Si cliente y proveedor comparten dirección se envía un solo correo (el del primer rol de la matriz). */
export function resolveRecipients(event: BookingRoutingKey, context: BookingEmailContext): BookingEmailRecipient[] {
  const seen = new Set<string>();
  const recipients: BookingEmailRecipient[] = [];
  for (const role of MATRIX[event]) {
    const person = role === 'customer' ? context.customer : context.provider;
    const key = person.email.trim().toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    recipients.push({ role, name: person.name, email: person.email });
  }
  return recipients;
}

export function linkFor(role: RecipientRole, frontendUrl: string): string {
  const base = frontendUrl.replace(/\/+$/, '');
  return role === 'customer' ? `${base}/bookings/me` : `${base}/provider`;
}
