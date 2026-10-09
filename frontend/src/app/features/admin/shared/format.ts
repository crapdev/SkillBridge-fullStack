import { HttpErrorResponse } from '@angular/common/http';

/** "Ana María Pérez" → "AP"; "calborparra" → "CA". */
export function initials(text: string): string {
  const words = text.trim().split(/[\s._-]+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

// Tonos de la paleta del sitio (azules, cian, navy) con texto legible sobre cada uno
const AVATAR_TONES = [
  ['#dbeafe', '#1d4ed8'], ['#cffafe', '#0e7490'], ['#e0e7ff', '#4338ca'], ['#fee4e2', '#b42318'],
  ['#dcfce7', '#15803d'], ['#fef3c7', '#b45309'], ['#f3e8ff', '#7e22ce'], ['#e2e8f0', '#1e293b']
];

/** Color fijo por persona: el mismo texto siempre produce el mismo tono. */
export function avatarTone(seed: string): { bg: string; fg: string } {
  let hash = 0;
  for (const ch of seed) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  const [bg, fg] = AVATAR_TONES[Math.abs(hash) % AVATAR_TONES.length];
  return { bg, fg };
}

const dateFormat = new Intl.DateTimeFormat('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
const relativeFormat = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });

/** "09 oct 2026" (sin los "de" que añade el formato largo en español). */
export function formatDate(iso: string): string {
  const parts = dateFormat.formatToParts(new Date(iso));
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find(p => p.type === type)?.value.replace('.', '') ?? '';
  return `${get('day')} ${get('month')} ${get('year')}`;
}

/** "Hace 2 horas", "ayer", "hace 3 semanas". */
export function timeAgo(iso: string): string {
  const seconds = (new Date(iso).getTime() - Date.now()) / 1000;
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000], ['month', 2_592_000], ['week', 604_800], ['day', 86_400], ['hour', 3_600], ['minute', 60]
  ];
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) {
      const text = relativeFormat.format(Math.round(seconds / size), unit);
      return text.charAt(0).toUpperCase() + text.slice(1);
    }
  }
  return 'Hace un momento';
}

/** Mensaje seguro para el usuario a partir de un error HTTP (ProblemDetail del backend). */
export function errorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  switch (error.status) {
    case 0: return 'No fue posible conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
    // Hoy el backend responde 401 también a endpoints que aún no existen (ver docs del panel)
    case 401: return 'El servidor no tiene disponible esta información todavía (o tu sesión ya no es válida).';
    case 403: return 'Tu sesión no tiene permisos de administrador para esta acción.';
    case 404: return error.error?.detail || 'Esta función aún no está disponible en el servidor.';
    default: return error.status < 500 && error.error?.detail ? error.error.detail : fallback;
  }
}
