import type { Channel } from 'amqplib';
import { BOOKING_EMAIL_ROUTING_KEYS, BOOKING_EMAILS_DLX_TYPE, BOOKING_EVENTS_EXCHANGE_TYPE } from './booking-events.constants';
import { BookingEventsConfig } from './booking-events.config';

/**
 * Declara la topología del correo de reservas. Todas las llamadas assert* son idempotentes: si el objeto ya
 * existe con las mismas propiedades no pasa nada, y amqp-connection-manager la vuelve a ejecutar en cada
 * reconexión. Si una propiedad no coincide, RabbitMQ responde PRECONDITION_FAILED: por eso el exchange se
 * declara exactamente como en Java (topic, durable, sin auto-delete) y los argumentos de las colas son fijos.
 *
 *   booking.events (topic) ──booking.created|paid|cancelled|completed──▶ booking.emails.queue
 *        booking.emails.queue ──rechazado/agotado──▶ booking.emails.dlx (fanout) ──▶ booking.emails.dlq
 *        booking.emails.retry.queue ──caduca (expiration del mensaje)──▶ exchange por defecto ──▶ booking.emails.queue
 */
export async function setupBookingEmailTopology(channel: Channel, cfg: BookingEventsConfig): Promise<void> {
  await channel.assertExchange(cfg.exchange, BOOKING_EVENTS_EXCHANGE_TYPE, { durable: true, autoDelete: false });

  // Fanout: llega a la DLQ cualquier routing key, y el mensaje conserva la original para diagnosticarlo
  await channel.assertExchange(cfg.dlx, BOOKING_EMAILS_DLX_TYPE, { durable: true, autoDelete: false });
  await channel.assertQueue(cfg.dlq, { durable: true });
  await channel.bindQueue(cfg.dlq, cfg.dlx, '');

  await channel.assertQueue(cfg.queue, { durable: true, deadLetterExchange: cfg.dlx });
  for (const routingKey of BOOKING_EMAIL_ROUTING_KEYS) {
    await channel.bindQueue(cfg.queue, cfg.exchange, routingKey);
  }

  // La espera de cada reintento va en el propio mensaje (expiration), no como x-message-ttl de la cola:
  // así cambiar BOOKING_EMAIL_RETRY_DELAY_MS no exige borrar la cola (evita PRECONDITION_FAILED).
  // Al caducar vuelve por el exchange por defecto SOLO a nuestra cola; si volviera por booking.events,
  // la cola de notificaciones de Java recibiría el evento repetido.
  await channel.assertQueue(cfg.retryQueue, {
    durable: true,
    deadLetterExchange: '',
    deadLetterRoutingKey: cfg.queue
  });
}
