import { Logger, OnApplicationBootstrap, OnModuleDestroy } from '@nestjs/common';
import type { ChannelWrapper } from 'amqp-connection-manager';
import type { Channel, ConsumeMessage } from 'amqplib';
import { RabbitMqService } from '../rabbitmq/rabbitmq.service';
import { classifyError } from './booking-email.errors';
import { setupBookingEmailTopology } from './booking-email.topology';
import { BookingEmailProcessor } from './booking-email.processor';
import { isBookingEmailRoutingKey, parseBookingEvent } from './booking-event.parser';
import { BookingEventsConfig } from './booking-events.config';
import { HEADER_ATTEMPT, HEADER_LAST_ERROR, HEADER_ORIGINAL_ROUTING_KEY } from './booking-events.constants';

export type Decision =
  | { action: 'ack' }
  | { action: 'retry'; nextAttempt: number; reason: string }
  | { action: 'dead'; reason: string };

const SHUTDOWN_GRACE_MS = 10_000;

/**
 * Consume booking.emails.queue con ACK manual. Nunca confirma un mensaje antes de terminar su procesamiento y
 * nunca deja escapar una excepción (un fallo de un mensaje no detiene el consumidor):
 * - éxito -> ack
 * - error reintentable con intentos disponibles -> copia en la cola de reintentos (espera y vuelve) + ack
 * - error permanente o intentos agotados -> copia en la DLX con el motivo + ack (si no se puede, nack sin requeue)
 */
export class BookingEventsConsumer implements OnApplicationBootstrap, OnModuleDestroy {
  private readonly logger = new Logger(BookingEventsConsumer.name);
  private channel?: ChannelWrapper;
  private consumerTag?: string;
  /** Aumenta en cada (re)conexión: un ack sobre un canal nuevo de un mensaje del anterior cerraría el canal. */
  private generation = 0;
  private inFlight = 0;

  constructor(
    private readonly rabbit: RabbitMqService,
    private readonly processor: BookingEmailProcessor,
    private readonly cfg: BookingEventsConfig,
    private readonly contextConfigured: boolean
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    if (!this.rabbit.isEnabled()) {
      return; // RabbitMqService ya avisó en el log
    }
    if (!this.contextConfigured) {
      // Sin contexto no hay destinatarios: mejor no consumir (los mensajes esperan en la cola durable) que
      // mandar todo a la DLQ aparentando que funciona
      this.logger.warn('BOOKING_CONTEXT_URL no está configurada: el consumidor de correos de reservas no se inicia');
      return;
    }
    this.channel = this.rabbit.createChannel({
      name: 'booking-emails',
      confirm: true,
      json: false,
      publishTimeout: 10_000,
      setup: async (ch: Channel) => {
        this.generation++;
        await setupBookingEmailTopology(ch, this.cfg);
      }
    });
    const { consumerTag } = await this.channel.consume(this.cfg.queue, (msg) => this.onMessage(msg), {
      noAck: false,
      prefetch: this.cfg.prefetch
    });
    this.consumerTag = consumerTag;
    this.logger.log(`Consumiendo ${this.cfg.queue} (prefetch=${this.cfg.prefetch}, intentos=${this.cfg.maxAttempts})`);
  }

  async onModuleDestroy(): Promise<void> {
    if (!this.channel) return;
    // 1) dejar de recibir, 2) esperar a los mensajes en curso, 3) cerrar el canal (lo no confirmado vuelve a la cola)
    if (this.consumerTag) {
      await this.channel.cancel(this.consumerTag).catch(() => undefined);
    }
    const deadline = Date.now() + SHUTDOWN_GRACE_MS;
    while (this.inFlight > 0 && Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, 50));
    }
    if (this.inFlight > 0) {
      this.logger.warn(`Apagado con ${this.inFlight} mensaje(s) en curso: RabbitMQ los reentregará`);
    }
    await this.channel.close().catch(() => undefined);
  }

  /** Punto de entrada de cada mensaje. Público para los tests. */
  async onMessage(msg: ConsumeMessage): Promise<void> {
    const generation = this.generation;
    this.inFlight++;
    try {
      const decision = await this.decide(msg);
      await this.apply(msg, decision, generation);
    } catch (err) {
      // Solo llega aquí si falla el propio ack/publicación: se registra y el mensaje se reentregará
      this.logger.error(`Error inesperado gestionando un mensaje: ${classifyError(err).reason}`);
    } finally {
      this.inFlight--;
    }
  }

  async decide(msg: ConsumeMessage): Promise<Decision> {
    const headers = msg.properties.headers ?? {};
    const routingKey = String(headers[HEADER_ORIGINAL_ROUTING_KEY] ?? msg.fields.routingKey);
    const attempt = Math.max(1, Number(headers[HEADER_ATTEMPT]) || 1);
    let bookingId = 'desconocido';
    try {
      if (!isBookingEmailRoutingKey(routingKey)) {
        return { action: 'dead', reason: 'unknown_routing_key' };
      }
      const event = parseBookingEvent(msg.content);
      bookingId = event.bookingId;
      this.logger.log(`Evento recibido: evento=${routingKey} bookingId=${bookingId} intento=${attempt}`);
      const summary = await this.processor.process(routingKey, event);
      this.logger.log(`Evento procesado: evento=${routingKey} bookingId=${bookingId} enviados=${summary.sent} yaEnviados=${summary.alreadySent}`);
      return { action: 'ack' };
    } catch (err) {
      const { retryable, reason } = classifyError(err);
      if (!retryable) {
        return { action: 'dead', reason };
      }
      if (attempt >= this.cfg.maxAttempts) {
        return { action: 'dead', reason: `max_attempts:${reason}` };
      }
      this.logger.warn(`Reintento programado: evento=${routingKey} bookingId=${bookingId} intento=${attempt} motivo=${reason}`);
      return { action: 'retry', nextAttempt: attempt + 1, reason };
    }
  }

  private async apply(msg: ConsumeMessage, decision: Decision, generation: number): Promise<void> {
    const channel = this.channel!;
    if (decision.action === 'retry' || decision.action === 'dead') {
      const headers = {
        ...(msg.properties.headers ?? {}),
        [HEADER_ORIGINAL_ROUTING_KEY]: String(msg.properties.headers?.[HEADER_ORIGINAL_ROUTING_KEY] ?? msg.fields.routingKey),
        [HEADER_ATTEMPT]: decision.action === 'retry' ? decision.nextAttempt : Number(msg.properties.headers?.[HEADER_ATTEMPT]) || 1,
        [HEADER_LAST_ERROR]: decision.reason
      };
      const options = {
        persistent: true,
        contentType: msg.properties.contentType,
        contentEncoding: msg.properties.contentEncoding,
        headers
      };
      try {
        if (decision.action === 'retry') {
          await channel.sendToQueue(this.cfg.retryQueue, msg.content, { ...options, expiration: String(this.cfg.retryDelayMs) });
        } else {
          await channel.publish(this.cfg.dlx, '', msg.content, options);
          this.logger.error(`Mensaje enviado a la DLQ: evento=${headers[HEADER_ORIGINAL_ROUTING_KEY]} motivo=${decision.reason}`);
        }
      } catch (err) {
        // No se pudo copiar: rechazar sin requeue lo lleva a la DLQ por la DLX de la cola (nunca un bucle)
        this.logger.error(`No se pudo publicar (${decision.action}); se rechaza hacia la DLQ: ${classifyError(err).reason}`);
        this.settle(msg, generation, 'nack');
        return;
      }
    }
    this.settle(msg, generation, 'ack');
  }

  private settle(msg: ConsumeMessage, generation: number, how: 'ack' | 'nack'): void {
    if (generation !== this.generation) {
      // El canal en que llegó ya no existe: RabbitMQ lo devolvió a la cola y lo reentregará (la idempotencia
      // evita el correo repetido)
      this.logger.warn('El canal se reconectó durante el procesamiento: el mensaje se reentregará');
      return;
    }
    if (how === 'ack') this.channel!.ack(msg);
    else this.channel!.nack(msg, false, false);
  }
}
