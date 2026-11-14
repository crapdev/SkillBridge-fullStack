import { EmailMessage, EmailSenderPort } from '../email/email-sender.port';
import { BookingEmailError } from './booking-email.errors';
import { BookingEmailProcessor } from './booking-email.processor';
import { BookingEvent } from './booking-event.types';
import { BOOKING_EMAIL_ROUTING_KEYS } from './booking-events.constants';
import { BookingContextPort, BookingEmailContext } from './context/booking-context.port';
import { ClaimResult, DeliveryKey, DeliveryLedgerPort } from './idempotency/delivery-ledger.port';

/** Registro en memoria con la misma semántica que PostgresDeliveryLedger (probado aparte con PostgreSQL real). */
class InMemoryLedger implements DeliveryLedgerPort {
  rows = new Map<string, { status: string; attempts: number }>();
  failNextMarkSent = false;
  private id = (k: DeliveryKey) => `${k.bookingId}|${k.event}|${k.role}`;

  async registerPending(keys: DeliveryKey[]) {
    for (const k of keys) if (!this.rows.has(this.id(k))) this.rows.set(this.id(k), { status: 'PENDING', attempts: 0 });
  }
  async claim(k: DeliveryKey): Promise<ClaimResult> {
    const row = this.rows.get(this.id(k)) ?? { status: 'PENDING', attempts: 0 };
    if (row.status === 'SENT') return { outcome: 'already_sent' };
    if (row.status === 'PROCESSING') return { outcome: 'in_progress' };
    this.rows.set(this.id(k), { status: 'PROCESSING', attempts: row.attempts + 1 });
    return { outcome: 'claimed', attempts: row.attempts + 1 };
  }
  async markSent(k: DeliveryKey) {
    if (this.failNextMarkSent) {
      this.failNextMarkSent = false;
      throw new Error('connection terminated');
    }
    this.rows.get(this.id(k))!.status = 'SENT';
  }
  async markFailed(k: DeliveryKey) {
    const row = this.rows.get(this.id(k))!;
    if (row.status === 'PROCESSING') row.status = 'FAILED';
  }
  status(k: DeliveryKey) {
    return this.rows.get(this.id(k))?.status;
  }
}

const EVENT: BookingEvent = {
  bookingId: 'dd48632f-fc67-40e5-8ee5-53d3aa1aa103',
  offeringId: '2c54a055-6f9f-469e-903b-c9aca2cd0830',
  customerId: '4082d605-e364-4a56-b40b-976b07c2ac17',
  scheduledAt: new Date('2026-11-20T15:00:00Z'),
  occurredAt: new Date('2026-11-14T00:00:00Z')
};

const CONTEXT: BookingEmailContext = {
  bookingId: EVENT.bookingId,
  scheduledAt: EVENT.scheduledAt,
  durationMinutes: 60,
  offering: { title: 'Mentoría Java Backend' },
  customer: { name: 'Ana', email: 'ana@example.com' },
  provider: { name: 'Luis', email: 'luis@example.com' }
};

function setup() {
  const ledger = new InMemoryLedger();
  const sent: EmailMessage[] = [];
  const sender = { send: jest.fn(async (m: EmailMessage) => void sent.push(m)) } as unknown as EmailSenderPort & { send: jest.Mock };
  const context = { getEmailContext: jest.fn().mockResolvedValue(CONTEXT) } as unknown as BookingContextPort & { getEmailContext: jest.Mock };
  const processor = new BookingEmailProcessor(context, ledger, sender, 'http://localhost:4200');
  return { ledger, sent, sender, context, processor };
}

async function errorOf(promise: Promise<unknown>): Promise<BookingEmailError> {
  try {
    await promise;
  } catch (err) {
    expect(err).toBeInstanceOf(BookingEmailError);
    return err as BookingEmailError;
  }
  throw new Error('se esperaba un error');
}

describe('BookingEmailProcessor', () => {
  it.each(BOOKING_EMAIL_ROUTING_KEYS)('%s envía un correo al cliente y otro al proveedor con sus enlaces', async (event) => {
    const { processor, sent, context } = setup();
    expect(await processor.process(event, EVENT)).toEqual({ sent: 2, alreadySent: 0 });
    expect(context.getEmailContext).toHaveBeenCalledWith(EVENT.bookingId);
    expect(sent.map((m) => [m.to, m.recipientName])).toEqual([['ana@example.com', 'Ana'], ['luis@example.com', 'Luis']]);
    expect(sent[0].text).toContain('http://localhost:4200/bookings/me');
    expect(sent[1].text).toContain('http://localhost:4200/provider');
  });

  it('el mismo evento recibido dos veces no repite los correos', async () => {
    const { processor, sender } = setup();
    await processor.process('booking.paid', EVENT);
    expect(await processor.process('booking.paid', EVENT)).toEqual({ sent: 0, alreadySent: 2 });
    expect(sender.send).toHaveBeenCalledTimes(2);
  });

  it('si falla el contexto no se envía nada y el error conserva su clasificación', async () => {
    const { processor, context, sender } = setup();
    context.getEmailContext.mockRejectedValue(BookingEmailError.retryable('context_timeout'));
    expect(await errorOf(processor.process('booking.created', EVENT))).toMatchObject({ retryable: true, reason: 'context_timeout' });
    expect(sender.send).not.toHaveBeenCalled();
  });

  it('si el proveedor de correo falla para uno, el otro se envía igual y el reintento solo envía el pendiente', async () => {
    const { processor, sender, ledger } = setup();
    sender.send.mockImplementationOnce(async () => {
      throw Object.assign(new Error('503'), { name: 'EmailDeliveryError', retryable: true });
    });
    expect(await errorOf(processor.process('booking.created', EVENT))).toMatchObject({ retryable: true, reason: 'EmailDeliveryError' });
    expect(ledger.status({ bookingId: EVENT.bookingId, event: 'booking.created', role: 'customer' })).toBe('FAILED');
    expect(ledger.status({ bookingId: EVENT.bookingId, event: 'booking.created', role: 'provider' })).toBe('SENT');

    sender.send.mockClear();
    expect(await processor.process('booking.created', EVENT)).toEqual({ sent: 1, alreadySent: 1 });
    expect(sender.send).toHaveBeenCalledTimes(1);
    expect(sender.send.mock.calls[0][0].to).toBe('ana@example.com');
  });

  it('un rechazo permanente del proveedor se propaga como permanente', async () => {
    const { processor, sender } = setup();
    sender.send.mockRejectedValue(Object.assign(new Error('invalid'), { name: 'EmailDeliveryError', retryable: false }));
    expect((await errorOf(processor.process('booking.paid', EVENT))).retryable).toBe(false);
  });

  it('si otro intento está enviando el mismo correo, se reprograma sin enviar', async () => {
    const { processor, ledger, sender } = setup();
    await ledger.claim({ bookingId: EVENT.bookingId, event: 'booking.paid', role: 'customer' });
    expect(await errorOf(processor.process('booking.paid', EVENT))).toMatchObject({ retryable: true, reason: 'delivery_in_progress' });
    expect(sender.send.mock.calls.map(([m]) => m.to)).toEqual(['luis@example.com']);
  });

  it('si falla guardar SENT tras enviar, el error sube para reintentar (puede haber un duplicado: al menos una vez)', async () => {
    const { processor, ledger } = setup();
    ledger.failNextMarkSent = true;
    await expect(processor.process('booking.cancelled', EVENT)).rejects.toThrow('connection terminated');
  });

  it('nunca pone direcciones de correo en los logs', async () => {
    const { processor } = setup();
    const spy = jest.spyOn(require('@nestjs/common').Logger.prototype, 'log');
    await processor.process('booking.paid', EVENT);
    const logged = spy.mock.calls.map((c) => String(c[0])).join('\n');
    expect(logged).not.toMatch(/@example\.com/);
    expect(logged).toContain(EVENT.bookingId);
    spy.mockRestore();
  });
});
