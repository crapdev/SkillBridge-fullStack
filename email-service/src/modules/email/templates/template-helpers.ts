// Helpers comunes de las plantillas de correo: escapar datos del usuario y formatear fechas, precios y correos.

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// Ej.: "lunes, 17 de noviembre de 2026, 3:00 p. m." en hora de Colombia, esté donde esté el servidor
export function formatBogotaDateTime(date: Date): string {
  return new Intl.DateTimeFormat('es-CO', {
    timeZone: 'America/Bogota',
    dateStyle: 'full',
    timeStyle: 'short',
  }).format(date);
}

// Ej.: "$ 120.000" (pesos sin decimales)
export function formatCop(value: string | number): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(Number(value));
}

// Para logs: "ana@gmail.com" → "a***@gmail.com"
export function maskEmail(email: string): string {
  const [user, domain] = email.split('@');
  if (!domain) return '***';
  return `${user.charAt(0)}***@${domain}`;
}
