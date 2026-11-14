import { BookingEmailError, classifyError, sanitizeReason } from './booking-email.errors';
import { isBookingEmailRoutingKey, parseBookingEvent } from './booking-event.parser';

// Cuerpo real generado por Jackson2JsonMessageConverter (spring-amqp 3.2.7) para el record BookingEvent de Java
const JAVA_BODY =
  '{"bookingId":"dd48632f-fc67-40e5-8ee5-53d3aa1aa103","offeringId":"2c54a055-6f9f-469e-903b-c9aca2cd0830",' +
  '"customerId":"4082d605-e364-4a56-b40b-976b07c2ac17","scheduledAt":1795186800.000000000,"occurredAt":1794615143.739838807}';

function expectPermanent(fn: () => unknown, reason: string): void {
  try {
    fn();
  } catch (err) {
    expect(err).toBeInstanceOf(BookingEmailError);
    expect((err as BookingEmailError).retryable).toBe(false);
    expect((err as BookingEmailError).reason).toBe(reason);
    return;
  }
  throw new Error('se esperaba un error');
}

describe('parseBookingEvent', () => {
  it('acepta el formato real de Java (Instant como segundos epoch con decimales)', () => {
    const event = parseBookingEvent(Buffer.from(JAVA_BODY));
    expect(event.bookingId).toBe('dd48632f-fc67-40e5-8ee5-53d3aa1aa103');
    expect(event.scheduledAt.toISOString()).toBe('2026-11-20T15:00:00.000Z');
    expect(event.occurredAt.toISOString()).toBe('2026-11-14T00:12:23.740Z');
  });

  it('acepta Instant en ISO-8601 por si el conversor de Java cambia', () => {
    const body = JSON.parse(JAVA_BODY);
    body.scheduledAt = '2026-11-20T15:00:00Z';
    expect(parseBookingEvent(Buffer.from(JSON.stringify(body))).scheduledAt.toISOString()).toBe('2026-11-20T15:00:00.000Z');
  });

  it('ignora campos desconocidos (Java puede añadir campos sin romper el consumidor)', () => {
    const body = { ...JSON.parse(JAVA_BODY), extra: 'x' };
    expect(parseBookingEvent(Buffer.from(JSON.stringify(body))).customerId).toBe('4082d605-e364-4a56-b40b-976b07c2ac17');
  });

  it('JSON inválido es un error permanente', () => {
    expectPermanent(() => parseBookingEvent(Buffer.from('{no es json')), 'invalid_json');
  });

  it.each([
    ['un array', '[]'],
    ['null', 'null'],
    ['bookingId ausente', JSON.stringify({ ...JSON.parse(JAVA_BODY), bookingId: undefined })],
    ['bookingId que no es UUID', JSON.stringify({ ...JSON.parse(JAVA_BODY), bookingId: '123' })],
    ['fecha de texto inválida', JSON.stringify({ ...JSON.parse(JAVA_BODY), scheduledAt: 'mañana' })],
    ['fecha vacía', JSON.stringify({ ...JSON.parse(JAVA_BODY), occurredAt: '' })],
    ['fecha booleana', JSON.stringify({ ...JSON.parse(JAVA_BODY), occurredAt: true })]
  ])('payload con %s es un error permanente', (_label, body) => {
    expectPermanent(() => parseBookingEvent(Buffer.from(body)), 'invalid_payload');
  });
});

describe('isBookingEmailRoutingKey', () => {
  it('solo acepta los cuatro eventos del alcance', () => {
    expect(['booking.created', 'booking.paid', 'booking.cancelled', 'booking.completed'].every(isBookingEmailRoutingKey)).toBe(true);
    expect(isBookingEmailRoutingKey('booking.payment-failed')).toBe(false);
    expect(isBookingEmailRoutingKey('booking.emails.queue')).toBe(false);
  });
});

describe('classifyError', () => {
  it('respeta la clasificación de BookingEmailError', () => {
    expect(classifyError(BookingEmailError.permanent('invalid_json'))).toEqual({ retryable: false, reason: 'invalid_json' });
    expect(classifyError(BookingEmailError.retryable('context_timeout'))).toEqual({ retryable: true, reason: 'context_timeout' });
  });

  it('respeta la propiedad retryable de errores ajenos (EmailDeliveryError de Persona A)', () => {
    const providerDown = Object.assign(new Error('503'), { name: 'EmailDeliveryError', retryable: true });
    const rejected = Object.assign(new Error('bad address'), { name: 'EmailDeliveryError', retryable: false });
    expect(classifyError(providerDown)).toEqual({ retryable: true, reason: 'EmailDeliveryError' });
    expect(classifyError(rejected)).toEqual({ retryable: false, reason: 'EmailDeliveryError' });
  });

  it('un error desconocido se reintenta (con intentos limitados)', () => {
    expect(classifyError(new TypeError('x'))).toEqual({ retryable: true, reason: 'TypeError' });
    expect(classifyError('texto')).toEqual({ retryable: true, reason: 'unknown_error' });
  });

  it('sanitizeReason elimina texto libre', () => {
    expect(sanitizeReason('fallo con ana@correo.com y espacios')).not.toContain(' ');
    expect(sanitizeReason('')).toBe('unknown_error');
  });
});
