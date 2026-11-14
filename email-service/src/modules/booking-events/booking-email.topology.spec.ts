import type { Channel } from 'amqplib';
import { setupBookingEmailTopology } from './booking-email.topology';
import { BookingEventsConfig } from './booking-events.config';

const cfg: BookingEventsConfig = {
  exchange: 'booking.events',
  queue: 'booking.emails.queue',
  dlx: 'booking.emails.dlx',
  dlq: 'booking.emails.dlq',
  retryQueue: 'booking.emails.retry.queue',
  prefetch: 5,
  maxAttempts: 5,
  retryDelayMs: 30000
};

function fakeChannel() {
  return {
    assertExchange: jest.fn().mockResolvedValue({}),
    assertQueue: jest.fn().mockResolvedValue({}),
    bindQueue: jest.fn().mockResolvedValue({})
  };
}

describe('setupBookingEmailTopology', () => {
  it('declara booking.events igual que Java (topic, durable, sin auto-delete)', async () => {
    const ch = fakeChannel();
    await setupBookingEmailTopology(ch as unknown as Channel, cfg);
    expect(ch.assertExchange).toHaveBeenCalledWith('booking.events', 'topic', { durable: true, autoDelete: false });
  });

  it('enlaza la cola de correo solo con los cuatro eventos del alcance', async () => {
    const ch = fakeChannel();
    await setupBookingEmailTopology(ch as unknown as Channel, cfg);
    const keys = ch.bindQueue.mock.calls.filter(([q]) => q === cfg.queue).map(([, , key]) => key);
    expect(keys).toEqual(['booking.created', 'booking.paid', 'booking.cancelled', 'booking.completed']);
    expect(keys).not.toContain('booking.payment-failed');
  });

  it('la cola de correo descarta hacia una DLX fanout enlazada a la DLQ', async () => {
    const ch = fakeChannel();
    await setupBookingEmailTopology(ch as unknown as Channel, cfg);
    expect(ch.assertQueue).toHaveBeenCalledWith(cfg.queue, { durable: true, deadLetterExchange: cfg.dlx });
    expect(ch.assertExchange).toHaveBeenCalledWith(cfg.dlx, 'fanout', { durable: true, autoDelete: false });
    expect(ch.bindQueue).toHaveBeenCalledWith(cfg.dlq, cfg.dlx, '');
  });

  it('la cola de reintentos devuelve los mensajes solo a nuestra cola, no a booking.events', async () => {
    const ch = fakeChannel();
    await setupBookingEmailTopology(ch as unknown as Channel, cfg);
    expect(ch.assertQueue).toHaveBeenCalledWith(cfg.retryQueue, {
      durable: true,
      deadLetterExchange: '',
      deadLetterRoutingKey: cfg.queue
    });
    // sin x-message-ttl en la cola: la espera va en cada mensaje
    const retryArgs = ch.assertQueue.mock.calls.find(([q]) => q === cfg.retryQueue)?.[1];
    expect(retryArgs).not.toHaveProperty('messageTtl');
  });

  it('no declara ni enlaza nada de la cola de notificaciones de Java', async () => {
    const ch = fakeChannel();
    await setupBookingEmailTopology(ch as unknown as Channel, cfg);
    const touched = [...ch.assertQueue.mock.calls, ...ch.bindQueue.mock.calls].map(([q]) => q);
    expect(touched).not.toContain('booking.notifications.queue');
  });
});
