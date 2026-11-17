// Errores que deciden qué hace el consumidor con el mensaje.
// Reintentable: se vuelve a intentar (red, proveedor caído, reserva aún no visible). Permanente: directo a la DLQ.
export class RetryableError extends Error {
  readonly retryable = true;
}

export class PermanentError extends Error {
  readonly retryable = false;
}

// Cualquier error con `retryable === false` (por ejemplo, el EmailDeliveryError de Persona A) es permanente;
// el resto se trata como reintentable, porque un fallo desconocido suele ser temporal.
export function isRetryable(error: unknown): boolean {
  return !(error instanceof Object && (error as { retryable?: unknown }).retryable === false);
}
