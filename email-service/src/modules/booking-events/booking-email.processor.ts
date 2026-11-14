import { Logger } from '@nestjs/common';
import { EmailSenderPort } from '../email/email-sender.port';
import { BookingEmailError, classifyError, ErrorClassification } from './booking-email.errors';
import { linkFor, resolveRecipients } from './booking-email.recipients';
import { BookingEvent, BookingRoutingKey } from './booking-event.types';
import { BookingContextPort } from './context/booking-context.port';
import { DeliveryKey, DeliveryLedgerPort } from './idempotency/delivery-ledger.port';
import { renderBookingEmail } from './templates/booking-email.templates';

export interface ProcessSummary {
  sent: number;
  alreadySent: number;
}

/**
 * Convierte un evento de reserva en sus correos. Es idempotente: cada correo se reclama en el registro antes de
 * enviarse, así que reprocesar el mismo evento solo envía lo que todavía no salió.
 * Lanza BookingEmailError si algo queda pendiente; el consumidor decide si reintenta o lo manda a la DLQ.
 */
export class BookingEmailProcessor {
  private readonly logger = new Logger(BookingEmailProcessor.name);

  constructor(
    private readonly context: BookingContextPort,
    private readonly ledger: DeliveryLedgerPort,
    private readonly sender: EmailSenderPort,
    private readonly frontendUrl: string
  ) {}

  async process(event: BookingRoutingKey, payload: BookingEvent): Promise<ProcessSummary> {
    const context = await this.context.getEmailContext(payload.bookingId);
    const recipients = resolveRecipients(event, context);
    const keyOf = (role: DeliveryKey['role']): DeliveryKey => ({ bookingId: payload.bookingId, event, role });

    await this.ledger.registerPending(recipients.map((r) => keyOf(r.role)));

    const summary: ProcessSummary = { sent: 0, alreadySent: 0 };
    const failures: ErrorClassification[] = [];

    for (const recipient of recipients) {
      const key = keyOf(recipient.role);
      const claim = await this.ledger.claim(key);
      if (claim.outcome === 'already_sent') {
        summary.alreadySent++;
        this.logger.log(`Correo ya enviado, se omite: evento=${event} bookingId=${payload.bookingId} rol=${recipient.role}`);
        continue;
      }
      if (claim.outcome === 'in_progress') {
        failures.push({ retryable: true, reason: 'delivery_in_progress' });
        continue;
      }

      const email = renderBookingEmail(event, recipient.role, {
        recipientName: recipient.name,
        customerName: context.customer.name,
        offeringTitle: context.offering.title,
        scheduledAt: context.scheduledAt,
        durationMinutes: context.durationMinutes,
        link: linkFor(recipient.role, this.frontendUrl)
      });

      try {
        await this.sender.send({ to: recipient.email, recipientName: recipient.name, ...email });
      } catch (err) {
        const failure = classifyError(err);
        failures.push(failure);
        this.logger.warn(
          `Fallo al enviar: evento=${event} bookingId=${payload.bookingId} rol=${recipient.role} intento=${claim.attempts} motivo=${failure.reason} reintentable=${failure.retryable}`
        );
        await this.ledger.markFailed(key, failure.reason);
        continue;
      }
      // Si guardar SENT falla, el error sube y el mensaje se reintenta: tras vencer la reserva el correo se
      // reenvía. Es el único caso de duplicado (entrega al menos una vez); ver README.
      await this.ledger.markSent(key);
      summary.sent++;
      this.logger.log(`Correo enviado: evento=${event} bookingId=${payload.bookingId} rol=${recipient.role} intento=${claim.attempts}`);
    }

    if (failures.length > 0) {
      // Si alguno puede arreglarse reintentando, se reintenta (los ya enviados no se repiten)
      const retryable = failures.find((f) => f.retryable);
      const worst = retryable ?? failures[0];
      throw new BookingEmailError(worst.reason, worst.retryable);
    }
    return summary;
  }
}
