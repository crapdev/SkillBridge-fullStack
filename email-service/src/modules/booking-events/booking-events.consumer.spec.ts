import type { ConsumeMessage } from 'amqplib';
import { RabbitMqService } from '../rabbitmq/rabbitmq.service';
import { BookingEmailError } from './booking-email.errors';
import { BookingEmailProcessor } from './booking-email.processor';
import { BookingEventsConfig } from './booking-events.config';
import { BookingEventsConsumer } from './booking-events.consumer';

const cfg: BookingEventsConfig = {
  exchange: 'booking.events',
  queue: 'booking.emails.queue',
  dlx: 'booking.emails.dlx',
  dlq: 'booking.emails.dlq',
  retryQueue: 'booking.emails.retry.queue',
  prefetch: 5,
  maxAttempts: 3,
  retryDelayMs: 30000
};

const BODY = JSON.stringify({
  bookingId: 'dd48632f-fc67-40e5-8ee5-53d3aa1aa103',
  offeringId: '2c54a055-6f9f-469e-903b-c9aca2cd0830',
  customerId: '4082d605-e364-4a56-b40b-976b07c2ac17',
  scheduledAt: 1795186800.0,
  occurredAt: 1794615143.739
});

function message(routingKey: string, body = BODY, headers: Record<string, unknown> = {}): ConsumeMessage {
  return {
    content: Buffer.from(body),
    fields: { routingKey, deliveryTag: 1, redelivered: false, exchange: 'booking.events', consumerTag: 't' },
    properties: { headers, contentType: 'application/json', contentEncoding: 'UTF-8' }
  } as unknown as ConsumeMessage;
}

async function setup(opts: { rabbitEnabled?: boolean; contextConfigured?: boolean } = {}) {
  let setupFn: ((ch: unknown) => Promise<void>) | undefined;
  const channel = {
    consume: jest.fn().mockResolvedValue({ consumerTag: 'tag-1' }),
    ack: jest.fn(),
    nack: jest.fn(),
    sendToQueue: jest.fn().mockResolvedValue(true),
    publish: jest.fn().mockResolvedValue(true),
    cancel: jest.fn().mockResolvedValue(undefined),
    close: jest.fn().mockResolvedValue(undefined)
  };
  const rabbit = {
    isEnabled: () => opts.rabbitEnabled ?? true,
    createChannel: jest.fn((o: { setup: (ch: unknown) => Promise<void> }) => {
      setupFn = o.setup;
      return channel;
    })
  } as unknown as RabbitMqService & { createChannel: jest.Mock };
  const processor = { process: jest.fn().mockResolvedValue({ sent: 2, alreadySent: 0 }) } as unknown as BookingEmailProcessor & { process: jest.Mock };
  const consumer = new BookingEventsConsumer(rabbit, processor, cfg, opts.contextConfigured ?? true);
  await consumer.onApplicationBootstrap();
  // simula la conexión: amqp-connection-manager ejecuta el setup (topología) sobre el canal real
  const fakeAmqpChannel = { assertExchange: jest.fn(), assertQueue: jest.fn(), bindQueue: jest.fn() };
  const reconnect = () => setupFn?.(fakeAmqpChannel);
  await reconnect();
  return { consumer, channel, processor, rabbit, reconnect };
}

describe('BookingEventsConsumer', () => {
  it('se suscribe a booking.emails.queue con ACK manual y prefetch', async () => {
    const { channel } = await setup();
    expect(channel.consume).toHaveBeenCalledWith('booking.emails.queue', expect.any(Function), { noAck: false, prefetch: 5 });
  });

  it('sin RabbitMQ configurado no crea canal', async () => {
    const { rabbit } = await setup({ rabbitEnabled: false });
    expect(rabbit.createChannel).not.toHaveBeenCalled();
  });

  it('sin contexto configurado no consume (los mensajes esperan en la cola)', async () => {
    const { rabbit } = await setup({ contextConfigured: false });
    expect(rabbit.createChannel).not.toHaveBeenCalled();
  });

  it.each(['booking.created', 'booking.paid', 'booking.cancelled', 'booking.completed'])(
    '%s se procesa y se confirma (ACK) después de procesar',
    async (key) => {
      const { consumer, channel, processor } = await setup();
      let ackedBeforeProcessEnded = false;
      processor.process.mockImplementation(async () => {
        ackedBeforeProcessEnded = channel.ack.mock.calls.length > 0;
        return { sent: 2, alreadySent: 0 };
      });
      const msg = message(key);
      await consumer.onMessage(msg);
      expect(processor.process).toHaveBeenCalledWith(key, expect.objectContaining({ bookingId: 'dd48632f-fc67-40e5-8ee5-53d3aa1aa103' }));
      expect(ackedBeforeProcessEnded).toBe(false);
      expect(channel.ack).toHaveBeenCalledWith(msg);
      expect(channel.sendToQueue).not.toHaveBeenCalled();
    }
  );

  it('JSON inválido va directo a la DLQ con el motivo, sin reintentos', async () => {
    const { consumer, channel, processor } = await setup();
    const msg = message('booking.created', '{roto');
    await consumer.onMessage(msg);
    expect(processor.process).not.toHaveBeenCalled();
    expect(channel.publish).toHaveBeenCalledWith('booking.emails.dlx', '', msg.content,
      expect.objectContaining({ persistent: true, headers: expect.objectContaining({ 'x-email-last-error': 'invalid_json', 'x-original-routing-key': 'booking.created' }) }));
    expect(channel.sendToQueue).not.toHaveBeenCalled();
    expect(channel.ack).toHaveBeenCalledWith(msg);
  });

  it('una routing key desconocida va a la DLQ', async () => {
    const { consumer, channel } = await setup();
    await consumer.onMessage(message('booking.payment-failed'));
    expect(channel.publish.mock.calls[0][3].headers['x-email-last-error']).toBe('unknown_routing_key');
  });

  it('un error reintentable programa un reintento con espera, intento+1 y routing key original', async () => {
    const { consumer, channel, processor } = await setup();
    processor.process.mockRejectedValue(BookingEmailError.retryable('context_timeout'));
    const msg = message('booking.paid');
    await consumer.onMessage(msg);
    expect(channel.sendToQueue).toHaveBeenCalledWith('booking.emails.retry.queue', msg.content, expect.objectContaining({
      expiration: '30000',
      persistent: true,
      headers: expect.objectContaining({ 'x-email-attempt': 2, 'x-original-routing-key': 'booking.paid', 'x-email-last-error': 'context_timeout' })
    }));
    expect(channel.ack).toHaveBeenCalledWith(msg);
    expect(channel.publish).not.toHaveBeenCalled();
  });

  it('un mensaje que vuelve de la cola de reintentos usa la routing key original de la cabecera', async () => {
    const { consumer, processor } = await setup();
    await consumer.onMessage(message('booking.emails.queue', BODY, { 'x-original-routing-key': 'booking.cancelled', 'x-email-attempt': 2 }));
    expect(processor.process).toHaveBeenCalledWith('booking.cancelled', expect.anything());
  });

  it('al agotar los intentos el mensaje va a la DLQ (sin ciclo infinito)', async () => {
    const { consumer, channel, processor } = await setup();
    processor.process.mockRejectedValue(BookingEmailError.retryable('EmailDeliveryError'));
    await consumer.onMessage(message('booking.emails.queue', BODY, { 'x-original-routing-key': 'booking.paid', 'x-email-attempt': 3 }));
    expect(channel.sendToQueue).not.toHaveBeenCalled();
    expect(channel.publish.mock.calls[0][3].headers).toMatchObject({ 'x-email-last-error': 'max_attempts:EmailDeliveryError', 'x-email-attempt': 3 });
  });

  it('un error permanente del procesamiento va a la DLQ sin reintentos', async () => {
    const { consumer, channel, processor } = await setup();
    processor.process.mockRejectedValue(BookingEmailError.permanent('context_unauthorized'));
    await consumer.onMessage(message('booking.created'));
    expect(channel.sendToQueue).not.toHaveBeenCalled();
    expect(channel.publish.mock.calls[0][3].headers['x-email-last-error']).toBe('context_unauthorized');
  });

  it('si no puede publicar en la DLX, hace NACK sin requeue (la DLX de la cola lo lleva a la DLQ)', async () => {
    const { consumer, channel } = await setup();
    channel.publish.mockRejectedValue(new Error('channel closed'));
    const msg = message('booking.created', '{roto');
    await consumer.onMessage(msg);
    expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
    expect(channel.ack).not.toHaveBeenCalled();
  });

  it('un error inesperado no detiene el consumidor: el siguiente mensaje se procesa', async () => {
    const { consumer, channel } = await setup();
    channel.ack.mockImplementationOnce(() => {
      throw new Error('boom');
    });
    await expect(consumer.onMessage(message('booking.paid'))).resolves.toBeUndefined();
    await consumer.onMessage(message('booking.paid'));
    expect(channel.ack).toHaveBeenCalledTimes(2);
  });

  it('si el canal se reconecta durante el procesamiento no confirma en el canal nuevo', async () => {
    const { consumer, channel, processor, reconnect } = await setup();
    processor.process.mockImplementation(async () => {
      await reconnect(); // se cae la conexión y se vuelve a ejecutar el setup mientras se procesa
      return { sent: 1, alreadySent: 0 };
    });
    await consumer.onMessage(message('booking.paid'));
    expect(channel.ack).not.toHaveBeenCalled();
    expect(channel.nack).not.toHaveBeenCalled();
  });

  it('al apagar deja de consumir, espera lo que está en curso y cierra el canal', async () => {
    const { consumer, channel, processor } = await setup();
    let release!: () => void;
    processor.process.mockImplementation(() => new Promise((r) => (release = () => r({ sent: 1, alreadySent: 0 }))));
    const inFlight = consumer.onMessage(message('booking.paid'));
    const shutdown = consumer.onModuleDestroy();
    await new Promise((r) => setTimeout(r, 20));
    expect(channel.cancel).toHaveBeenCalledWith('tag-1');
    expect(channel.close).not.toHaveBeenCalled();
    release();
    await inFlight;
    await shutdown;
    expect(channel.ack).toHaveBeenCalled();
    expect(channel.close).toHaveBeenCalled();
  });
});
