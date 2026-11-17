import type { ConsumeMessage } from 'amqplib';
import { PermanentError, RetryableError } from './booking-email.errors';
import { BookingEmailService } from './booking-email.service';
import { BookingEventsConsumer } from './booking-events.consumer';

const BOOKING_ID = '11111111-2222-3333-4444-555555555555';

const message = (content: string, routingKey = 'booking.created') =>
  ({ content: Buffer.from(content), fields: { routingKey }, properties: {} }) as unknown as ConsumeMessage;

const event = (overrides: Record<string, unknown> = {}) =>
  // Java puede mandar las fechas como números (Jackson sin configurar)
  JSON.stringify({ bookingId: BOOKING_ID, offeringId: 'x', customerId: 'y', scheduledAt: 1763409600.0, ...overrides });

describe('BookingEventsConsumer', () => {
  let bookingEmails: { handle: jest.Mock };
  let channel: { ack: jest.Mock; nack: jest.Mock };
  let consumer: BookingEventsConsumer;
  let waits: number[];

  beforeEach(() => {
    bookingEmails = { handle: jest.fn().mockResolvedValue('processed') };
    channel = { ack: jest.fn(), nack: jest.fn() };
    consumer = new BookingEventsConsumer(bookingEmails as unknown as BookingEmailService);
    waits = [];
    consumer.sleep = async (ms) => {
      waits.push(ms);
    };
  });

  it('éxito → ack', async () => {
    const msg = message(event());
    await consumer.handle(msg, channel);
    expect(bookingEmails.handle).toHaveBeenCalledWith('booking.created', BOOKING_ID);
    expect(channel.ack).toHaveBeenCalledWith(msg);
    expect(channel.nack).not.toHaveBeenCalled();
  });

  it('routing key fuera de la matriz → ack (no es un error)', async () => {
    bookingEmails.handle.mockResolvedValue('ignored');
    await consumer.handle(message(event(), 'booking.payment-failed'), channel);
    expect(channel.ack).toHaveBeenCalled();
  });

  it('JSON roto → nack sin reencolar (a la DLQ) y sin llamar al servicio', async () => {
    const msg = message('{no es json');
    await consumer.handle(msg, channel);
    expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
    expect(bookingEmails.handle).not.toHaveBeenCalled();
  });

  it.each([{ bookingId: undefined }, { bookingId: 'no-es-uuid' }, { bookingId: 123 }])(
    'bookingId inválido (%o) → DLQ enseguida',
    async (overrides) => {
      await consumer.handle(message(event(overrides)), channel);
      expect(channel.nack).toHaveBeenCalledWith(expect.anything(), false, false);
      expect(bookingEmails.handle).not.toHaveBeenCalled();
    },
  );

  it('error reintentable → 3 intentos (esperas de 2 s y 10 s) y luego DLQ', async () => {
    bookingEmails.handle.mockRejectedValue(new RetryableError('caído'));
    await consumer.handle(message(event()), channel);
    expect(bookingEmails.handle).toHaveBeenCalledTimes(3);
    expect(waits).toEqual([2000, 10000]);
    expect(channel.nack).toHaveBeenCalledWith(expect.anything(), false, false);
    expect(channel.ack).not.toHaveBeenCalled();
  });

  it('reserva no encontrada al principio → reintenta y, si aparece, ack', async () => {
    bookingEmails.handle
      .mockRejectedValueOnce(new RetryableError('Reserva no encontrada todavía'))
      .mockResolvedValueOnce('processed');
    await consumer.handle(message(event()), channel);
    expect(bookingEmails.handle).toHaveBeenCalledTimes(2);
    expect(waits).toEqual([2000]);
    expect(channel.ack).toHaveBeenCalled();
  });

  it('error permanente → DLQ enseguida, sin reintentos', async () => {
    bookingEmails.handle.mockRejectedValue(new PermanentError('rechazado'));
    await consumer.handle(message(event()), channel);
    expect(bookingEmails.handle).toHaveBeenCalledTimes(1);
    expect(waits).toEqual([]);
    expect(channel.nack).toHaveBeenCalledWith(expect.anything(), false, false);
  });
});
