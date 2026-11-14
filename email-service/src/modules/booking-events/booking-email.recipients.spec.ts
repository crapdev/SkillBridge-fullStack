import { linkFor, resolveRecipients } from './booking-email.recipients';
import { BOOKING_EMAIL_ROUTING_KEYS } from './booking-events.constants';
import { BookingEmailContext } from './context/booking-context.port';

const context = (customerEmail = 'ana@example.com', providerEmail = 'luis@example.com'): BookingEmailContext => ({
  bookingId: 'dd48632f-fc67-40e5-8ee5-53d3aa1aa103',
  scheduledAt: new Date('2026-11-20T15:00:00Z'),
  durationMinutes: 60,
  offering: { title: 'Mentoría Java Backend' },
  customer: { name: 'Ana', email: customerEmail },
  provider: { name: 'Luis', email: providerEmail }
});

describe('resolveRecipients', () => {
  it.each(BOOKING_EMAIL_ROUTING_KEYS)('%s avisa al cliente y al proveedor, cada uno con sus datos', (event) => {
    expect(resolveRecipients(event, context())).toEqual([
      { role: 'customer', name: 'Ana', email: 'ana@example.com' },
      { role: 'provider', name: 'Luis', email: 'luis@example.com' }
    ]);
  });

  it('si cliente y proveedor comparten dirección (sin importar mayúsculas) se envía un solo correo', () => {
    const recipients = resolveRecipients('booking.paid', context('Ana@Example.com', ' ana@example.com'));
    expect(recipients).toHaveLength(1);
    expect(recipients[0].role).toBe('customer');
  });
});

describe('linkFor', () => {
  it('el cliente va a Mis reservas y el proveedor a su panel (rutas reales del frontend)', () => {
    expect(linkFor('customer', 'http://localhost:4200/')).toBe('http://localhost:4200/bookings/me');
    expect(linkFor('provider', 'http://localhost:4200')).toBe('http://localhost:4200/provider');
  });
});
