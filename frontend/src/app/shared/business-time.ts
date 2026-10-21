/**
 * Zona horaria de negocio. El backend envía instantes en UTC; todo lo que se muestra o se agrupa
 * por día se interpreta en hora de Colombia, no en la zona del navegador de quien abre la página.
 */
export const BUSINESS_TIME_ZONE = 'America/Bogota';

/** Igual que new Intl.DateTimeFormat('es-CO', …) pero siempre en hora de Colombia. */
export function businessDateFormat(options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('es-CO', { ...options, timeZone: BUSINESS_TIME_ZONE });
}

// 'en-CA' formatea como "yyyy-mm-dd", justo el formato de clave que necesitamos
const dayKeyFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: BUSINESS_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit'
});

/** "2026-10-20T04:30:00Z" → "2026-10-19": el día en Colombia, para agrupar por fecha. */
export function businessDayKey(iso: string): string {
  return dayKeyFormat.format(new Date(iso));
}