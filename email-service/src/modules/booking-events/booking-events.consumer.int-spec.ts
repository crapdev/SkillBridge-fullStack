import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer, Server } from 'node:http';
import { AddressInfo } from 'node:net';
import { join } from 'node:path';
import { ConfigService } from '@nestjs/config';
import * as amqp from 'amqplib';
import { DataSource } from 'typeorm';
import { EmailMessage, EmailSenderPort } from '../email/email-sender.port';
import { RabbitMqService } from '../rabbitmq/rabbitmq.service';
import { BookingEmailProcessor } from './booking-email.processor';
import { BookingEventsConfig } from './booking-events.config';
import { BookingEventsConsumer } from './booking-events.consumer';
import { HttpBookingContextAdapter } from './context/http-booking-context.adapter';
import { PostgresDeliveryLedger } from './idempotency/postgres-delivery-ledger';

// Integración con RabbitMQ real (el del docker-compose principal) y PostgreSQL real.
// Requiere RABBITMQ_TEST_URL y DATABASE_TEST_URL; RABBITMQ_TEST_MANAGEMENT_URL para la prueba de reconexión.
const RABBIT = process.env.RABBITMQ_TEST_URL;
const DB = process.env.DATABASE_TEST_URL;
const MGMT = process.env.RABBITMQ_TEST_MANAGEMENT_URL; // p. ej. http://guest:guest@127.0.0.1:15672
const MIGRATION = join(__dirname, '../../../../backend/src/main/resources/db/migration/V22__email_booking_deliveries.sql');

const describeIfInfra = RABBIT && DB ? describe : describe.skip;
jest.setTimeout(60_000);

const waitFor = async (check: () => boolean | Promise<boolean>, timeoutMs = 15_000) => {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    if (await check()) return;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error('condición no alcanzada a tiempo');
};

describeIfInfra('BookingEventsConsumer (RabbitMQ y PostgreSQL reales)', () => {
  const suffix = randomUUID().slice(0, 8);
  // Nombres propios del test: nunca toca booking.events ni booking.notifications.queue
  const cfg: BookingEventsConfig = {
    exchange: `test.booking.events.${suffix}`,
    queue: `test.booking.emails.queue.${suffix}`,
    dlx: `test.booking.emails.dlx.${suffix}`,
    dlq: `test.booking.emails.dlq.${suffix}`,
    retryQueue: `test.booking.emails.retry.${suffix}`,
    prefetch: 5,
    maxAttempts: 2,
    retryDelayMs: 1000
  };

  let ds: DataSource;
  let server: Server;
  let contextStatus = 200;
  let contextCalls = 0;
  let admin: amqp.ChannelModel;
  let adminCh: amqp.Channel;
  let rabbit: RabbitMqService;
  let consumer: BookingEventsConsumer;
  const sent: EmailMessage[] = [];
  let senderFailures = 0;

  const bookingId = () => randomUUID();
  const body = (id: string) =>
    JSON.stringify({ bookingId: id, offeringId: randomUUID(), customerId: randomUUID(), scheduledAt: 1795186800.0, occurredAt: 1794615143.739 });
  // Igual que Java: JSON + cabecera __TypeId__ + persistente
  const publish = (key: string, content: string) =>
    adminCh.publish(cfg.exchange, key, Buffer.from(content), {
      contentType: 'application/json', contentEncoding: 'UTF-8', persistent: true, headers: { __TypeId__: 'BookingEvent' }
    });
  const sentFor = (id: string) => sent.filter((m) => m.text.includes('Mentoría Java Backend') && (m as EmailMessage & { id?: string }).id === id);
  const dlqMessages = async () => {
    const out: amqp.GetMessage[] = [];
    for (let m = await adminCh.get(cfg.dlq, { noAck: true }); m; m = await adminCh.get(cfg.dlq, { noAck: true })) out.push(m);
    return out;
  };

  const openAdmin = async () => {
    admin = await amqp.connect(RABBIT!);
    admin.on('error', () => undefined); // cierres forzados en la prueba de reconexión
    adminCh = await admin.createChannel();
    adminCh.on('error', () => undefined);
  };

  beforeAll(async () => {
    ds = await new DataSource({ type: 'postgres', url: DB, synchronize: false }).initialize();
    await ds.query('DROP SCHEMA IF EXISTS email CASCADE');
    await ds.query(readFileSync(MIGRATION, 'utf8'));

    // Endpoint de contexto falso: solo para el test (en producción lo expone Java)
    server = createServer((req, res) => {
      contextCalls++;
      const id = /\/internal\/bookings\/([^/]+)\/email-context/.exec(req.url ?? '')?.[1];
      if (contextStatus !== 200 || req.headers['x-internal-token'] !== 'token-test') {
        res.writeHead(req.headers['x-internal-token'] !== 'token-test' ? 401 : contextStatus).end();
        return;
      }
      res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({
        bookingId: id, scheduledAt: '2026-11-20T15:00:00Z', durationMinutes: 60,
        offering: { title: 'Mentoría Java Backend' },
        customer: { name: 'Ana', email: `ana+${id}@example.com` },
        provider: { name: 'Luis', email: `luis+${id}@example.com` }
      }));
    });
    await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
    const port = (server.address() as AddressInfo).port;

    // "Java" declara el exchange como en RabbitConfiguration (topic, durable): el consumidor debe ser compatible
    await openAdmin();
    await adminCh.assertExchange(cfg.exchange, 'topic', { durable: true, autoDelete: false });

    const sender: EmailSenderPort = {
      send: async (m: EmailMessage) => {
        if (senderFailures > 0) {
          senderFailures--;
          throw Object.assign(new Error('503'), { name: 'EmailDeliveryError', retryable: true });
        }
        const id = /ana\+([^@]+)@|luis\+([^@]+)@/.exec(m.to);
        sent.push({ ...m, id: id?.[1] ?? id?.[2] } as EmailMessage);
      }
    };
    rabbit = new RabbitMqService({ get: () => RABBIT } as unknown as ConfigService);
    const processor = new BookingEmailProcessor(
      new HttpBookingContextAdapter({ baseUrl: `http://127.0.0.1:${port}`, token: 'token-test', timeoutMs: 2000 }),
      new PostgresDeliveryLedger(ds, 30),
      sender,
      'http://localhost:4200'
    );
    consumer = new BookingEventsConsumer(rabbit, processor, cfg, true);
    await consumer.onApplicationBootstrap();
    // checkQueue sobre una cola inexistente cierra el canal (404): cada comprobación usa uno nuevo
    await waitFor(async () => {
      const probe = await admin.createChannel();
      probe.on('error', () => undefined);
      const exists = await probe.checkQueue(cfg.queue).then(() => true, () => false);
      await probe.close().catch(() => undefined);
      return exists;
    });
  });

  afterAll(async () => {
    await consumer?.onModuleDestroy();
    await rabbit?.onApplicationShutdown();
    if (admin) {
      const ch = await admin.createChannel();
      for (const q of [cfg.queue, cfg.dlq, cfg.retryQueue]) await ch.deleteQueue(q).catch(() => undefined);
      for (const e of [cfg.exchange, cfg.dlx]) await ch.deleteExchange(e).catch(() => undefined);
      await admin.close();
    }
    server?.close();
    await ds?.destroy();
  });

  beforeEach(() => {
    contextStatus = 200;
    senderFailures = 0;
  });

  it.each(['booking.created', 'booking.paid', 'booking.cancelled', 'booking.completed'])(
    '%s publicado en el exchange envía los dos correos y deja la cola vacía (ACK)',
    async (key) => {
      const id = bookingId();
      publish(key, body(id));
      await waitFor(() => sentFor(id).length === 2);
      expect(sentFor(id).map((m) => m.recipientName).sort()).toEqual(['Ana', 'Luis']);
      await waitFor(async () => (await adminCh.checkQueue(cfg.queue)).messageCount === 0);
      const rows = await ds.query('SELECT status FROM email.booking_email_deliveries WHERE booking_id = $1', [id]);
      expect(rows.map((r: { status: string }) => r.status)).toEqual(['SENT', 'SENT']);
    }
  );

  it('booking.payment-failed no llega a la cola de correo (no está enlazado)', async () => {
    const before = contextCalls;
    publish('booking.payment-failed', body(bookingId()));
    await new Promise((r) => setTimeout(r, 800));
    expect(contextCalls).toBe(before);
  });

  it('el mismo mensaje entregado dos veces (incluso a la vez) envía cada correo una sola vez', async () => {
    const id = bookingId();
    publish('booking.paid', body(id));
    publish('booking.paid', body(id));
    await waitFor(() => sentFor(id).length >= 2);
    await new Promise((r) => setTimeout(r, 3000)); // margen por si el duplicado llega en un reintento
    expect(sentFor(id)).toHaveLength(2);
  });

  it('JSON inválido acaba en la DLQ con el motivo y la routing key original', async () => {
    publish('booking.created', '{roto');
    let dead: amqp.GetMessage[] = [];
    await waitFor(async () => (dead = await dlqMessages()).length > 0);
    expect(dead[0].properties.headers).toMatchObject({ 'x-email-last-error': 'invalid_json', 'x-original-routing-key': 'booking.created' });
  });

  it('un fallo temporal del proveedor se reintenta tras la espera y luego se envía', async () => {
    const id = bookingId();
    senderFailures = 1;
    const start = Date.now();
    publish('booking.created', body(id));
    await waitFor(() => sentFor(id).length === 2);
    expect(Date.now() - start).toBeGreaterThanOrEqual(900); // pasó por la cola de reintentos (1 s)
    const attempts = await ds.query(
      "SELECT recipient_role, attempts FROM email.booking_email_deliveries WHERE booking_id = $1 ORDER BY recipient_role", [id]);
    expect(attempts).toEqual([{ recipient_role: 'CUSTOMER', attempts: 2 }, { recipient_role: 'PROVIDER', attempts: 1 }]);
  });

  it('si el contexto sigue fallando, tras agotar los intentos va a la DLQ', async () => {
    contextStatus = 503;
    publish('booking.cancelled', body(bookingId()));
    let dead: amqp.GetMessage[] = [];
    await waitFor(async () => (dead = await dlqMessages()).length > 0, 20_000);
    expect(dead[0].properties.headers).toMatchObject({ 'x-email-last-error': 'max_attempts:context_http_503', 'x-email-attempt': 2 });
  });

  it('se reconecta solo si RabbitMQ cierra la conexión y sigue consumiendo', async () => {
    if (!MGMT) return;
    const url = new URL(MGMT);
    const auth = 'Basic ' + Buffer.from(`${decodeURIComponent(url.username)}:${decodeURIComponent(url.password)}`).toString('base64');
    const base = `${url.protocol}//${url.host}`;
    const conns = (await (await fetch(`${base}/api/connections`, { headers: { Authorization: auth } })).json()) as Array<{ name: string; client_properties?: { product?: string } }>;
    // cierra todas las conexiones salvo la del canal de administración del test
    const ours = conns.filter((c) => c.client_properties?.product !== undefined);
    expect(ours.length).toBeGreaterThan(0);
    for (const c of ours) {
      await fetch(`${base}/api/connections/${encodeURIComponent(c.name)}`, { method: 'DELETE', headers: { Authorization: auth } });
    }
    await openAdmin();
    const id = bookingId();
    await new Promise((r) => setTimeout(r, 6000)); // reconnectTimeInSeconds = 5
    publish('booking.completed', body(id));
    await waitFor(() => sentFor(id).length === 2, 20_000);
  });
});
