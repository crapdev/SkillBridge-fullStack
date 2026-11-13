import { Injectable, Logger } from '@nestjs/common';
import type { ConsumeMessage } from 'amqplib';
import { isRetryable, PermanentError } from './booking-email.errors';
import { BookingEmailService } from './booking-email.service';
import { RETRY_DELAYS_MS } from './booking-events.constants';

// Lo único que necesita del canal: así los tests pasan un objeto simulado
export interface AckChannel {
  ack(message: ConsumeMessage): void;
  nack(message: ConsumeMessage, allUpTo?: boolean, requeue?: boolean): void;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

@Injectable()
export class BookingEventsConsumer {
  private readonly logger = new Logger(BookingEventsConsumer.name);
  // Se puede sustituir en los tests para no esperar de verdad
  sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

  constructor(private readonly bookingEmails: BookingEmailService) {}

  /**
   * ACK solo si todos los correos se enviaron (o ya estaban enviados).
   * Error reintentable: 3 intentos en total (esperas de 2 s y 10 s) y luego DLQ.
   * Error permanente: DLQ enseguida. nack(msg, false, false) = no reencolar → va a la DLX.
   */
  async handle(message: ConsumeMessage, channel: AckChannel): Promise<void> {
    const routingKey = message.fields.routingKey;
    let bookingId: string;
    try {
      bookingId = this.parseBookingId(message);
    } catch (error) {
      this.logger.error(`Mensaje inválido, a la DLQ; routingKey=${routingKey}: ${(error as Error).message}`);
      channel.nack(message, false, false);
      return;
    }

    for (let attempt = 1; ; attempt++) {
      try {
        const result = await this.bookingEmails.handle(routingKey, bookingId);
        if (result === 'ignored') this.logger.debug(`Routing key fuera de la matriz; routingKey=${routingKey}`);
        channel.ack(message);
        return;
      } catch (error) {
        const retryable = isRetryable(error);
        const canRetry = retryable && attempt <= RETRY_DELAYS_MS.length;
        this.logger.warn(
          `Intento ${attempt} fallido (${retryable ? 'reintentable' : 'permanente'}); routingKey=${routingKey} bookingId=${bookingId}`,
        );
        if (!canRetry) {
          this.logger.error(`A la DLQ tras ${attempt} intento(s); routingKey=${routingKey} bookingId=${bookingId}`);
          channel.nack(message, false, false);
          return;
        }
        await this.sleep(RETRY_DELAYS_MS[attempt - 1]);
      }
    }
  }

  // Solo se valida bookingId: las fechas de Java pueden llegar como números y se leen de la base de datos
  private parseBookingId(message: ConsumeMessage): string {
    let body: unknown;
    try {
      body = JSON.parse(message.content.toString('utf8'));
    } catch {
      throw new PermanentError('JSON inválido');
    }
    const bookingId = (body as { bookingId?: unknown } | null)?.bookingId;
    if (typeof bookingId !== 'string' || !UUID.test(bookingId)) {
      throw new PermanentError('Falta bookingId o no es un UUID');
    }
    return bookingId;
  }
}
