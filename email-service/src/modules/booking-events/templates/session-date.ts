// Las sesiones se agendan y se muestran en hora de Colombia, igual que en el frontend y en la campana de Java
export const BUSINESS_TIME_ZONE = 'America/Bogota';

const FORMATTER = new Intl.DateTimeFormat('es-CO', {
  timeZone: BUSINESS_TIME_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23'
});

/** "viernes 20 de noviembre de 2026 a las 10:00 (hora de Colombia)". Formato de 24 h, como la campana de Java. */
export function formatSessionDate(date: Date): string {
  const parts = Object.fromEntries(FORMATTER.formatToParts(date).map((p) => [p.type, p.value]));
  return `${parts.weekday} ${parts.day} de ${parts.month} de ${parts.year} a las ${parts.hour}:${parts.minute} (hora de Colombia)`;
}
