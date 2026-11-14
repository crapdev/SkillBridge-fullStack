import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { DataSource } from 'typeorm';
import { DeliveryKey } from './delivery-ledger.port';
import { PostgresDeliveryLedger } from './postgres-delivery-ledger';

// Integración con PostgreSQL real. Requiere DATABASE_TEST_URL (una base desechable: se borra el esquema email).
const URL = process.env.DATABASE_TEST_URL;
const MIGRATION = join(__dirname, '../../../../../backend/src/main/resources/db/migration/V22__email_booking_deliveries.sql');

const key = (over: Partial<DeliveryKey> = {}): DeliveryKey => ({
  bookingId: 'dd48632f-fc67-40e5-8ee5-53d3aa1aa103',
  event: 'booking.paid',
  role: 'customer',
  ...over
});

const describeIfDb = URL ? describe : describe.skip;

describeIfDb('PostgresDeliveryLedger (PostgreSQL real + migración V22)', () => {
  let ds: DataSource;
  let ledger: PostgresDeliveryLedger;

  const newDataSource = () => new DataSource({ type: 'postgres', url: URL, synchronize: false }).initialize();
  const statusOf = async (k: DeliveryKey) =>
    (await ds.query(
      'SELECT status, attempts, last_error, sent_at FROM email.booking_email_deliveries WHERE booking_id=$1 AND event_type=$2 AND recipient_role=$3',
      [k.bookingId, k.event, k.role.toUpperCase()]
    ))[0];

  beforeAll(async () => {
    ds = await newDataSource();
  });

  beforeEach(async () => {
    await ds.query('DROP SCHEMA IF EXISTS email CASCADE');
    await ds.query(readFileSync(MIGRATION, 'utf8'));
    ledger = new PostgresDeliveryLedger(ds, 60);
  });

  afterAll(async () => {
    await ds?.destroy();
  });

  it('el primer mensaje reclama el envío', async () => {
    expect(await ledger.claim(key())).toEqual({ outcome: 'claimed', attempts: 1 });
    expect((await statusOf(key())).status).toBe('PROCESSING');
  });

  it('el mismo mensaje recibido otra vez tras enviarse no se reenvía', async () => {
    await ledger.claim(key());
    await ledger.markSent(key(), 'msg-123');
    expect(await ledger.claim(key())).toEqual({ outcome: 'already_sent' });
    expect((await statusOf(key())).sent_at).not.toBeNull();
  });

  it('dos (o diez) entregas simultáneas del mismo mensaje: solo una reclama el envío', async () => {
    const results = await Promise.all(Array.from({ length: 10 }, () => ledger.claim(key())));
    expect(results.filter((r) => r.outcome === 'claimed')).toHaveLength(1);
    expect(results.filter((r) => r.outcome === 'in_progress')).toHaveLength(9);
  });

  it('tras reiniciar el servicio (nueva conexión) sigue reconociendo lo ya enviado', async () => {
    await ledger.claim(key());
    await ledger.markSent(key());
    const ds2 = await newDataSource();
    try {
      expect(await new PostgresDeliveryLedger(ds2, 60).claim(key())).toEqual({ outcome: 'already_sent' });
    } finally {
      await ds2.destroy();
    }
  });

  it('un envío fallido se puede reintentar y cuenta los intentos', async () => {
    await ledger.claim(key());
    await ledger.markFailed(key(), 'EmailDeliveryError');
    expect(await statusOf(key())).toMatchObject({ status: 'FAILED', last_error: 'EmailDeliveryError' });
    expect(await ledger.claim(key())).toEqual({ outcome: 'claimed', attempts: 2 });
  });

  it('si el proceso se cae a mitad de envío, otro intento lo retoma cuando vence la reserva', async () => {
    const shortLease = new PostgresDeliveryLedger(ds, 1);
    await shortLease.claim(key()); // se "cae": nunca marca SENT ni FAILED
    expect(await shortLease.claim(key())).toEqual({ outcome: 'in_progress' });
    await new Promise((r) => setTimeout(r, 1200));
    expect(await shortLease.claim(key())).toEqual({ outcome: 'claimed', attempts: 2 });
  });

  it('markFailed nunca degrada un correo ya enviado', async () => {
    await ledger.claim(key());
    await ledger.markSent(key());
    await ledger.markFailed(key(), 'tarde');
    expect((await statusOf(key())).status).toBe('SENT');
  });

  it('registerPending deja constancia sin pisar lo existente, y lo pendiente se puede reclamar', async () => {
    await ledger.claim(key({ role: 'provider' }));
    await ledger.markSent(key({ role: 'provider' }));
    await ledger.registerPending([key(), key({ role: 'provider' })]);
    await ledger.registerPending([key()]);
    expect((await statusOf(key())).status).toBe('PENDING');
    expect((await statusOf(key({ role: 'provider' }))).status).toBe('SENT');
    expect(await ledger.claim(key())).toEqual({ outcome: 'claimed', attempts: 1 });
  });

  it('cada evento y cada destinatario de la misma reserva son correos distintos', async () => {
    await ledger.claim(key());
    await ledger.markSent(key());
    expect((await ledger.claim(key({ event: 'booking.cancelled' }))).outcome).toBe('claimed');
    expect((await ledger.claim(key({ role: 'provider' }))).outcome).toBe('claimed');
  });

  it('la tabla rechaza estados y eventos fuera del contrato', async () => {
    await expect(
      ds.query(`INSERT INTO email.booking_email_deliveries (booking_id, event_type, recipient_role, status)
                VALUES ($1, 'booking.payment-failed', 'CUSTOMER', 'PENDING')`, [key().bookingId])
    ).rejects.toThrow(/check/i);
  });
});
