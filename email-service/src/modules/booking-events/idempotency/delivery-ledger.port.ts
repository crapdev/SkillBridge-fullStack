import { BookingRoutingKey } from '../booking-event.types';
import { RecipientRole } from '../templates/booking-email.templates';

/** Identifica un correo: la misma reserva, el mismo evento y el mismo destinatario nunca se envían dos veces. */
export interface DeliveryKey {
  bookingId: string;
  event: BookingRoutingKey;
  role: RecipientRole;
}

export type ClaimResult =
  /** Este intento es el dueño del envío: debe enviar y luego marcar SENT o FAILED. */
  | { outcome: 'claimed'; attempts: number }
  /** Ya se envió antes: no se repite. */
  | { outcome: 'already_sent' }
  /** Otro intento lo está enviando ahora mismo (o se cayó y su reserva aún no vence). */
  | { outcome: 'in_progress' };

export abstract class DeliveryLedgerPort {
  /** Deja constancia (PENDING) de los correos que genera un evento. No pisa filas existentes. */
  abstract registerPending(keys: DeliveryKey[]): Promise<void>;
  abstract claim(key: DeliveryKey): Promise<ClaimResult>;
  abstract markSent(key: DeliveryKey, providerMessageId?: string): Promise<void>;
  abstract markFailed(key: DeliveryKey, reason: string): Promise<void>;
}
