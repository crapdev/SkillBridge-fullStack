/**
 * Errores del procesamiento de un evento. La clasificación decide qué hace el consumidor:
 * - permanente: reintentar no lo arregla (JSON inválido, routing key desconocida...) -> DLQ directamente.
 * - reintentable: puede arreglarse solo (red, timeout, proveedor caído, reserva aún sin commit) -> cola de reintentos
 *   hasta BOOKING_EMAIL_MAX_ATTEMPTS, y después DLQ.
 * `reason` es un código corto y sin datos personales: es lo que va al log y a la cabecera del mensaje.
 */
export class BookingEmailError extends Error {
  constructor(
    readonly reason: string,
    readonly retryable: boolean,
    message?: string
  ) {
    super(message ?? reason);
    this.name = 'BookingEmailError';
  }

  static permanent(reason: string, message?: string): BookingEmailError {
    return new BookingEmailError(reason, false, message);
  }

  static retryable(reason: string, message?: string): BookingEmailError {
    return new BookingEmailError(reason, true, message);
  }
}

export interface ErrorClassification {
  retryable: boolean;
  reason: string;
}

/**
 * Cualquier error con una propiedad booleana `retryable` se respeta: así funciona el `EmailDeliveryError` que
 * definirá Persona A sin duplicar su clase aquí. Lo desconocido se trata como reintentable: el número de intentos
 * está limitado, y perder un correo por un fallo puntual es peor que reintentarlo unas pocas veces.
 */
export function classifyError(err: unknown): ErrorClassification {
  if (err instanceof BookingEmailError) {
    return { retryable: err.retryable, reason: err.reason };
  }
  if (err instanceof Error) {
    const retryable = (err as { retryable?: unknown }).retryable;
    return {
      retryable: typeof retryable === 'boolean' ? retryable : true,
      reason: sanitizeReason(err.name || 'Error')
    };
  }
  return { retryable: true, reason: 'unknown_error' };
}

/** Un código apto para logs y cabeceras: sin espacios ni texto libre que pudiera llevar datos personales. */
export function sanitizeReason(reason: string): string {
  return reason.replace(/[^A-Za-z0-9_.:-]/g, '_').slice(0, 80) || 'unknown_error';
}
