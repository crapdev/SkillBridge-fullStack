import { BookingEmailError } from '../booking-email.errors';
import { BookingContextPort, BookingEmailContext, BookingParticipant } from './booking-context.port';

export interface HttpBookingContextOptions {
  baseUrl: string;
  token?: string;
  timeoutMs: number;
}

/**
 * Pide el contexto al backend Java: GET {baseUrl}/internal/bookings/{id}/email-context con X-Internal-Token.
 * El token y el cuerpo de la respuesta (que trae emails) nunca se registran.
 */
export class HttpBookingContextAdapter implements BookingContextPort {
  constructor(
    private readonly options: HttpBookingContextOptions,
    private readonly fetchFn: typeof fetch = fetch
  ) {}

  async getEmailContext(bookingId: string): Promise<BookingEmailContext> {
    const url = `${this.options.baseUrl.replace(/\/+$/, '')}/internal/bookings/${encodeURIComponent(bookingId)}/email-context`;
    let response: Response;
    try {
      response = await this.fetchFn(url, {
        method: 'GET',
        headers: { Accept: 'application/json', ...(this.options.token ? { 'X-Internal-Token': this.options.token } : {}) },
        signal: AbortSignal.timeout(this.options.timeoutMs)
      });
    } catch (err) {
      const timeout = err instanceof Error && (err.name === 'TimeoutError' || err.name === 'AbortError');
      throw BookingEmailError.retryable(timeout ? 'context_timeout' : 'context_unreachable');
    }

    if (response.status === 404) {
      // Java publica el evento dentro de la transacción: puede llegar antes del commit. Si de verdad no existe,
      // se agotan los intentos y acaba en la DLQ.
      throw BookingEmailError.retryable('context_booking_not_found');
    }
    if (response.status === 401 || response.status === 403) {
      throw BookingEmailError.permanent('context_unauthorized', 'revisar INTERNAL_API_TOKEN');
    }
    if (response.status === 429 || response.status >= 500) {
      throw BookingEmailError.retryable(`context_http_${response.status}`);
    }
    if (!response.ok) {
      throw BookingEmailError.permanent(`context_http_${response.status}`);
    }

    let body: unknown;
    try {
      body = await response.json();
    } catch {
      throw BookingEmailError.permanent('context_invalid_response', 'la respuesta no es JSON');
    }
    return parseContext(body, bookingId);
  }
}

function parseContext(body: unknown, bookingId: string): BookingEmailContext {
  const invalid = (detail: string) => BookingEmailError.permanent('context_invalid_response', detail);
  if (!isRecord(body)) throw invalid('no es un objeto');
  if (body.bookingId !== bookingId) throw invalid('bookingId no coincide');

  const scheduledAt = typeof body.scheduledAt === 'string' ? new Date(body.scheduledAt) : undefined;
  if (!scheduledAt || Number.isNaN(scheduledAt.getTime())) throw invalid('scheduledAt');
  if (typeof body.durationMinutes !== 'number' || body.durationMinutes <= 0) throw invalid('durationMinutes');
  if (!isRecord(body.offering) || !nonEmpty(body.offering.title)) throw invalid('offering.title');

  return {
    bookingId,
    scheduledAt,
    durationMinutes: body.durationMinutes,
    offering: { title: body.offering.title },
    customer: participant(body.customer, 'customer', invalid),
    provider: participant(body.provider, 'provider', invalid)
  };
}

function participant(value: unknown, field: string, invalid: (d: string) => Error): BookingParticipant {
  if (!isRecord(value) || !nonEmpty(value.name) || !nonEmpty(value.email) || !value.email.includes('@')) {
    throw invalid(field);
  }
  return { name: value.name.trim(), email: value.email.trim() };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function nonEmpty(value: unknown): value is string {
  return typeof value === 'string' && value.trim() !== '';
}
