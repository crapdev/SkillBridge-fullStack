import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../email/email-sender.port';
import { isRetryable, PermanentError, RetryableError } from './booking-email.errors';
import { BookingEmailContext, BookingRecipientsRepository } from './booking-recipients.repository';
import { EmailLogRepository } from './email-log.repository';
import {
  BookingEmailAudience,
  BookingEmailKind,
  hasBookingTemplate,
  renderBookingEmail,
} from './templates/booking-email.templates';
import { maskEmail } from '../email/templates/template-helpers';

const KIND_BY_ROUTING_KEY: Record<string, BookingEmailKind> = {
  'booking.created': 'created',
  'booking.paid': 'paid',
  'booking.cancelled': 'cancelled',
  'booking.completed': 'completed',
};

interface Recipient {
  audience: BookingEmailAudience;
  id: string;
  name: string;
  email: string;
  counterpartName: string | null;
}

export type HandleResult = 'ignored' | 'processed';

@Injectable()
export class BookingEmailService {
  private readonly logger = new Logger(BookingEmailService.name);
  private readonly frontendUrl: string;

  constructor(
    private readonly bookings: BookingRecipientsRepository,
    private readonly emailLog: EmailLogRepository,
    private readonly sender: EmailSenderPort,
    config: ConfigService,
  ) {
    this.frontendUrl = config.getOrThrow<string>('FRONTEND_URL').replace(/\/+$/, '');
  }

  /**
   * Envía los correos de un evento según la matriz de la HU #38.
   * Lanza RetryableError / PermanentError para que el consumidor decida entre reintentar o mandar a la DLQ.
   */
  async handle(routingKey: string, bookingId: string): Promise<HandleResult> {
    const kind = KIND_BY_ROUTING_KEY[routingKey];
    if (!kind) return 'ignored'; // p. ej. booking.payment-failed: fuera de la matriz

    const context = await this.bookings.findContext(bookingId);
    // Java publica el evento antes del commit: la reserva puede no ser visible todavía
    if (!context) throw new RetryableError('Reserva no encontrada todavía');

    const errors: unknown[] = [];
    for (const recipient of this.recipientsFor(kind, context)) {
      try {
        await this.sendTo(kind, routingKey, recipient, context);
      } catch (error) {
        errors.push(error); // se sigue con el otro destinatario: cada uno tiene su propio registro
      }
    }

    if (errors.length > 0) {
      // Si alguno puede salir bien al reintentar, se reintenta; los que ya están SENT no se repiten
      const message = `${errors.length} correo(s) sin enviar`;
      throw errors.some(isRetryable) ? new RetryableError(message) : new PermanentError(message);
    }
    return 'processed';
  }

  private recipientsFor(kind: BookingEmailKind, ctx: BookingEmailContext): Recipient[] {
    const recipients: Recipient[] = [];
    if (hasBookingTemplate(kind, 'customer')) {
      recipients.push({ audience: 'customer', ...ctx.customer, counterpartName: ctx.provider?.name ?? null });
    }
    // Mentorías antiguas sin proveedor: solo se avisa al cliente
    if (ctx.provider && hasBookingTemplate(kind, 'provider')) {
      recipients.push({ audience: 'provider', ...ctx.provider, counterpartName: ctx.customer.name });
    }
    return recipients;
  }

  private async sendTo(
    kind: BookingEmailKind,
    routingKey: string,
    recipient: Recipient,
    ctx: BookingEmailContext,
  ): Promise<void> {
    const logId = await this.emailLog.claim(ctx.bookingId, routingKey, recipient.id);
    const target = `routingKey=${routingKey} bookingId=${ctx.bookingId} to=${maskEmail(recipient.email)}`;
    if (logId === null) {
      this.logger.log(`Ya enviado, se omite; ${target}`);
      return;
    }

    const { subject, html, text } = renderBookingEmail(kind, recipient.audience, {
      recipientName: recipient.name,
      counterpartName: recipient.counterpartName,
      offeringTitle: ctx.offeringTitle,
      scheduledAt: ctx.scheduledAt,
      durationMinutes: ctx.durationMinutes,
      price: ctx.price,
      bookingId: ctx.bookingId,
      bookingsUrl: `${this.frontendUrl}${recipient.audience === 'customer' ? '/bookings/me' : '/provider'}`,
    });

    try {
      await this.sender.send({ to: recipient.email, recipientName: recipient.name, subject, html, text });
    } catch (error) {
      const reason = error instanceof Error ? error.name : 'Error';
      await this.emailLog.markFailed(logId, reason);
      this.logger.warn(`Envío fallido (${reason}); ${target}`);
      throw error;
    }
    await this.emailLog.markSent(logId);
    this.logger.log(`Enviado; ${target}`);
  }
}
