import { BookingEmailError } from '../booking-email.errors';
import { HttpBookingContextAdapter } from './http-booking-context.adapter';

const BOOKING_ID = 'dd48632f-fc67-40e5-8ee5-53d3aa1aa103';
const VALID = {
  bookingId: BOOKING_ID,
  scheduledAt: '2026-11-20T15:00:00Z',
  durationMinutes: 60,
  offering: { title: 'Mentoría Java Backend' },
  customer: { name: 'Ana Cliente', email: 'ana@example.com' },
  provider: { name: 'Luis Proveedor', email: 'luis@example.com' }
};

function adapterReturning(response: Response | Error, token = 'secreto') {
  const fetchFn = jest.fn(async () => {
    if (response instanceof Error) throw response;
    return response;
  });
  const adapter = new HttpBookingContextAdapter({ baseUrl: 'http://backend:8080/', token, timeoutMs: 1000 }, fetchFn as unknown as typeof fetch);
  return { adapter, fetchFn };
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

async function errorOf(promise: Promise<unknown>): Promise<BookingEmailError> {
  try {
    await promise;
  } catch (err) {
    expect(err).toBeInstanceOf(BookingEmailError);
    return err as BookingEmailError;
  }
  throw new Error('se esperaba un error');
}

describe('HttpBookingContextAdapter', () => {
  it('pide el contexto con el token interno y devuelve los datos validados', async () => {
    const { adapter, fetchFn } = adapterReturning(json(VALID));
    const context = await adapter.getEmailContext(BOOKING_ID);

    expect(fetchFn).toHaveBeenCalledWith(
      `http://backend:8080/internal/bookings/${BOOKING_ID}/email-context`,
      expect.objectContaining({ method: 'GET', headers: expect.objectContaining({ 'X-Internal-Token': 'secreto' }) })
    );
    expect(context.scheduledAt.toISOString()).toBe('2026-11-20T15:00:00.000Z');
    expect(context.provider.email).toBe('luis@example.com');
  });

  it.each([
    [404, true, 'context_booking_not_found'],
    [500, true, 'context_http_500'],
    [503, true, 'context_http_503'],
    [429, true, 'context_http_429'],
    [401, false, 'context_unauthorized'],
    [403, false, 'context_unauthorized'],
    [400, false, 'context_http_400']
  ])('HTTP %i -> reintentable=%s (%s)', async (status, retryable, reason) => {
    const { adapter } = adapterReturning(json({}, status));
    const err = await errorOf(adapter.getEmailContext(BOOKING_ID));
    expect(err.retryable).toBe(retryable);
    expect(err.reason).toBe(reason);
  });

  it('un timeout es reintentable', async () => {
    const timeout = Object.assign(new Error('timeout'), { name: 'TimeoutError' });
    const err = await errorOf(adapterReturning(timeout).adapter.getEmailContext(BOOKING_ID));
    expect(err).toMatchObject({ retryable: true, reason: 'context_timeout' });
  });

  it('un error de red es reintentable', async () => {
    const err = await errorOf(adapterReturning(new TypeError('fetch failed')).adapter.getEmailContext(BOOKING_ID));
    expect(err).toMatchObject({ retryable: true, reason: 'context_unreachable' });
  });

  it.each([
    ['otro bookingId', { ...VALID, bookingId: 'otro' }],
    ['sin email del proveedor', { ...VALID, provider: { name: 'Luis' } }],
    ['email sin @', { ...VALID, customer: { name: 'Ana', email: 'ana' } }],
    ['sin título', { ...VALID, offering: {} }],
    ['fecha inválida', { ...VALID, scheduledAt: 'x' }]
  ])('respuesta %s es un error permanente de contrato', async (_label, body) => {
    const err = await errorOf(adapterReturning(json(body)).adapter.getEmailContext(BOOKING_ID));
    expect(err).toMatchObject({ retryable: false, reason: 'context_invalid_response' });
  });

  it('el mensaje de error no incluye datos personales de la respuesta', async () => {
    const err = await errorOf(adapterReturning(json({ ...VALID, customer: { name: 'Ana', email: 'ana' } })).adapter.getEmailContext(BOOKING_ID));
    expect(err.message).not.toContain('ana');
  });

  it('sin token no envía la cabecera', async () => {
    const { adapter, fetchFn } = adapterReturning(json(VALID), '');
    await adapter.getEmailContext(BOOKING_ID);
    const init = (fetchFn.mock.calls[0] as unknown as [string, RequestInit])[1];
    expect(init.headers).not.toHaveProperty('X-Internal-Token');
  });
});
