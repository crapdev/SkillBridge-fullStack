import { BookingRoutingKey } from '../booking-event.types';
import { BOOKING_EMAIL_ROUTING_KEYS } from '../booking-events.constants';
import { BookingTemplateData, RecipientRole, renderBookingEmail } from './booking-email.templates';
import { formatSessionDate } from './session-date';

const data = (over: Partial<BookingTemplateData> = {}): BookingTemplateData => ({
  recipientName: 'Ana',
  customerName: 'Ana Cliente',
  offeringTitle: 'Mentoría Java Backend',
  scheduledAt: new Date('2026-11-20T15:00:00Z'),
  durationMinutes: 60,
  link: 'http://localhost:4200/bookings/me',
  ...over
});

const ROLES: RecipientRole[] = ['customer', 'provider'];
const ALL = BOOKING_EMAIL_ROUTING_KEYS.flatMap((event) => ROLES.map((role) => [event, role] as [BookingRoutingKey, RecipientRole]));

describe('formatSessionDate', () => {
  it('muestra la hora de Colombia (UTC-5) en formato de 24 h', () => {
    // 15:00 UTC = 10:00 en Bogotá
    expect(formatSessionDate(new Date('2026-11-20T15:00:00Z'))).toBe('viernes 20 de noviembre de 2026 a las 10:00 (hora de Colombia)');
  });

  it('cambia de día cuando corresponde en Colombia', () => {
    // 03:30 UTC del 21 = 22:30 del 20 en Bogotá
    expect(formatSessionDate(new Date('2026-11-21T03:30:00Z'))).toContain('20 de noviembre de 2026 a las 22:30');
  });
});

describe('renderBookingEmail', () => {
  it.each(ALL)('%s para %s incluye asunto, título, fecha, duración y enlace en HTML y texto', (event, role) => {
    const email = renderBookingEmail(event, role, data());
    expect(email.subject).toContain('Mentoría Java Backend');
    for (const body of [email.html, email.text]) {
      expect(body).toContain('Hola, Ana');
      expect(body).toContain('Mentoría Java Backend');
      expect(body).toContain('viernes 20 de noviembre de 2026 a las 10:00');
      expect(body).toContain('60 minutos');
      expect(body).toContain('http://localhost:4200/bookings/me');
    }
    expect(email.text).not.toContain('<');
  });

  it.each(BOOKING_EMAIL_ROUTING_KEYS)('%s: el correo del proveedor dice qué cliente reservó', (event) => {
    expect(renderBookingEmail(event, 'provider', data({ recipientName: 'Luis' })).text).toContain('Ana Cliente');
  });

  it('cada evento y rol tiene un asunto distinto, salvo la cancelación (mismo hecho para ambos)', () => {
    const subjects = ALL.map(([event, role]) => renderBookingEmail(event, role, data()).subject);
    expect(new Set(subjects).size).toBe(subjects.length - 1);
  });

  it('el cliente con la reserva creada recibe el aviso de que falta pagar', () => {
    expect(renderBookingEmail('booking.created', 'customer', data()).text).toContain('completa el pago');
  });

  it('el cliente con la sesión completada recibe la invitación a calificar', () => {
    const email = renderBookingEmail('booking.completed', 'customer', data());
    expect(email.subject).toBe('¿Cómo te fue en Mentoría Java Backend?');
    expect(email.text).toContain('Calificar la sesión');
  });

  it('escapa en el HTML los textos que escribe el usuario', () => {
    const email = renderBookingEmail('booking.created', 'provider', data({ customerName: '<script>x</script>', offeringTitle: 'A & "B"' }));
    expect(email.html).not.toContain('<script>');
    expect(email.html).toContain('&lt;script&gt;');
    expect(email.html).toContain('A &amp; &quot;B&quot;');
    // el texto plano no se escapa: no es HTML
    expect(email.text).toContain('<script>x</script>');
  });
});
