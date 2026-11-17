import { BookingEmailData, hasBookingTemplate, renderBookingEmail } from './booking-email.templates';
import { formatCop, maskEmail } from '../../email/templates/template-helpers';

const data = (overrides: Partial<BookingEmailData> = {}): BookingEmailData => ({
  recipientName: 'Ana',
  counterpartName: 'Pedro',
  offeringTitle: 'Clases de Angular',
  scheduledAt: new Date('2026-11-17T20:00:00Z'), // 3:00 p. m. en Bogotá (UTC-5)
  durationMinutes: 60,
  price: '120000.00',
  bookingId: '11111111-2222-3333-4444-555555555555',
  bookingsUrl: 'http://localhost:4200/bookings/me',
  ...overrides,
});

describe('plantillas de reservas', () => {
  it('existen las 7 combinaciones de la HU #38 y no hay "completed" para el cliente', () => {
    for (const kind of ['created', 'paid', 'cancelled'] as const) {
      expect(hasBookingTemplate(kind, 'customer')).toBe(true);
      expect(hasBookingTemplate(kind, 'provider')).toBe(true);
    }
    expect(hasBookingTemplate('completed', 'provider')).toBe(true);
    expect(hasBookingTemplate('completed', 'customer')).toBe(false);
  });

  it('la fecha sale en hora de Colombia', () => {
    const { html, text } = renderBookingEmail('paid', 'customer', data());
    expect(text).toMatch(/3:00/);
    expect(text).toContain('17 de noviembre de 2026');
    expect(html).toContain('hora de Colombia');
  });

  it('escapa los datos que escribe el usuario (nombres y título)', () => {
    const { html } = renderBookingEmail(
      'created',
      'provider',
      data({ recipientName: '<b>Ana</b>', counterpartName: '<script>x</script>', offeringTitle: '<img src=x>' }),
    );
    expect(html).not.toContain('<script>');
    expect(html).not.toContain('<img');
    expect(html).not.toContain('<b>Ana');
    expect(html).toContain('&lt;script&gt;x&lt;/script&gt;');
  });

  it('el botón y la versión de texto llevan el enlace a Mis reservas', () => {
    const { html, text } = renderBookingEmail('cancelled', 'customer', data());
    expect(html).toContain('href="http://localhost:4200/bookings/me"');
    expect(text).toContain('http://localhost:4200/bookings/me');
  });

  it('el asunto depende del evento y del destinatario', () => {
    expect(renderBookingEmail('created', 'customer', data()).subject).toBe('Tu reserva fue creada · SkillBridge');
    expect(renderBookingEmail('completed', 'provider', data()).subject).toBe('Sesión completada · SkillBridge');
  });

  it('sin precio (reservas antiguas) no muestra la línea de precio', () => {
    expect(renderBookingEmail('paid', 'customer', data({ price: null })).text).not.toContain('Precio');
  });

  it('formatCop y maskEmail', () => {
    expect(formatCop('120000.00').replace(/\s/g, ' ')).toBe('$ 120.000');
    expect(maskEmail('ana@gmail.com')).toBe('a***@gmail.com');
    expect(maskEmail('sin-arroba')).toBe('***');
  });
});
