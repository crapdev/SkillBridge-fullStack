import { ConfigService } from '@nestjs/config';
import { EmailSenderPort } from '../email/email-sender.port';
import { PermanentError, RetryableError } from './booking-email.errors';
import { BookingEmailService } from './booking-email.service';
import { BookingEmailContext, BookingRecipientsRepository } from './booking-recipients.repository';
import { EmailLogRepository } from './email-log.repository';

const BOOKING_ID = '11111111-2222-3333-4444-555555555555';

const context = (overrides: Partial<BookingEmailContext> = {}): BookingEmailContext => ({
  bookingId: BOOKING_ID,
  scheduledAt: new Date('2026-11-17T20:00:00Z'),
  price: '120000.00',
  offeringTitle: 'Clases de Angular',
  durationMinutes: 60,
  customer: { id: 'c-1', name: 'Ana Cliente', email: 'ana@test.com' },
  provider: { id: 'p-1', name: 'Pedro Mentor', email: 'pedro@test.com' },
  ...overrides,
});

describe('BookingEmailService', () => {
  let bookings: { findContext: jest.Mock };
  let emailLog: { claim: jest.Mock; markSent: jest.Mock; markFailed: jest.Mock };
  let sender: { send: jest.Mock };
  let service: BookingEmailService;

  const recipients = () => sender.send.mock.calls.map(([message]) => message.to);

  beforeEach(() => {
    bookings = { findContext: jest.fn().mockResolvedValue(context()) };
    let nextId = 1;
    emailLog = {
      claim: jest.fn().mockImplementation(async () => String(nextId++)),
      markSent: jest.fn().mockResolvedValue(undefined),
      markFailed: jest.fn().mockResolvedValue(undefined),
    };
    sender = { send: jest.fn().mockResolvedValue(undefined) };
    const config = { getOrThrow: jest.fn().mockReturnValue('http://localhost:4200/') } as unknown as ConfigService;
    service = new BookingEmailService(
      bookings as unknown as BookingRecipientsRepository,
      emailLog as unknown as EmailLogRepository,
      sender as unknown as EmailSenderPort,
      config,
    );
  });

  describe('matriz de destinatarios (HU #38)', () => {
    it.each(['booking.created', 'booking.paid', 'booking.cancelled'])('%s → cliente y proveedor', async (key) => {
      await expect(service.handle(key, BOOKING_ID)).resolves.toBe('processed');
      expect(recipients()).toEqual(['ana@test.com', 'pedro@test.com']);
    });

    it('booking.completed → solo el proveedor', async () => {
      await service.handle('booking.completed', BOOKING_ID);
      expect(recipients()).toEqual(['pedro@test.com']);
    });

    it('mentoría sin proveedor → solo el cliente', async () => {
      bookings.findContext.mockResolvedValue(context({ provider: null }));
      await service.handle('booking.paid', BOOKING_ID);
      expect(recipients()).toEqual(['ana@test.com']);
    });

    it('completed sin proveedor → nadie, y se da por procesado', async () => {
      bookings.findContext.mockResolvedValue(context({ provider: null }));
      await expect(service.handle('booking.completed', BOOKING_ID)).resolves.toBe('processed');
      expect(sender.send).not.toHaveBeenCalled();
    });

    it('routing key fuera de la matriz (booking.payment-failed) → ignorado, sin consultar la BD', async () => {
      await expect(service.handle('booking.payment-failed', BOOKING_ID)).resolves.toBe('ignored');
      expect(bookings.findContext).not.toHaveBeenCalled();
      expect(sender.send).not.toHaveBeenCalled();
    });
  });

  it('cada destinatario recibe su enlace: cliente → /bookings/me, proveedor → /provider', async () => {
    await service.handle('booking.created', BOOKING_ID);
    const [customerMail, providerMail] = sender.send.mock.calls.map(([m]) => m);
    expect(customerMail.text).toContain('http://localhost:4200/bookings/me');
    expect(providerMail.text).toContain('http://localhost:4200/provider');
    expect(customerMail.subject).toBe('Tu reserva fue creada · SkillBridge');
    expect(providerMail.subject).toBe('Tienes una reserva nueva · SkillBridge');
  });

  it('reserva no encontrada (Java aún no hizo commit) → error reintentable', async () => {
    bookings.findContext.mockResolvedValue(null);
    await expect(service.handle('booking.created', BOOKING_ID)).rejects.toBeInstanceOf(RetryableError);
  });

  describe('idempotencia', () => {
    it('si el reclamo no devuelve fila (ya SENT) no se envía nada', async () => {
      emailLog.claim.mockResolvedValue(null);
      await expect(service.handle('booking.paid', BOOKING_ID)).resolves.toBe('processed');
      expect(sender.send).not.toHaveBeenCalled();
    });

    it('reclama con (reserva, routing key, destinatario) y marca SENT', async () => {
      await service.handle('booking.cancelled', BOOKING_ID);
      expect(emailLog.claim).toHaveBeenCalledWith(BOOKING_ID, 'booking.cancelled', 'c-1');
      expect(emailLog.claim).toHaveBeenCalledWith(BOOKING_ID, 'booking.cancelled', 'p-1');
      expect(emailLog.markSent).toHaveBeenCalledTimes(2);
    });

    it('si falla el del proveedor, el del cliente queda SENT y se lanza reintentable', async () => {
      sender.send.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('timeout'));
      await expect(service.handle('booking.paid', BOOKING_ID)).rejects.toBeInstanceOf(RetryableError);
      expect(emailLog.markSent).toHaveBeenCalledWith('1');
      expect(emailLog.markFailed).toHaveBeenCalledWith('2', 'Error');
    });

    it('un error con retryable=false (proveedor de correo lo rechaza) → permanente', async () => {
      sender.send.mockRejectedValue(Object.assign(new Error('rechazado'), { retryable: false }));
      await expect(service.handle('booking.paid', BOOKING_ID)).rejects.toBeInstanceOf(PermanentError);
    });
  });
});
