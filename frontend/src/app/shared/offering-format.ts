import { OfferingLevel, WeekDay } from '../core/offering.service';
import { businessDateFormat } from './business-time';

/**
 * Textos y reglas de la ficha de la mentoría (HU-20) compartidos por el panel del proveedor,
 * el detalle del servicio y la reserva. Las reglas coinciden con las del backend (OfferingDraft).
 */

/** Comisión que cobra la plataforma sobre el valor de cada mentoría */
export const PLATFORM_COMMISSION = 0.10;

export const MIN_TOPICS = 2;
export const MAX_TOPICS = 12;

/** Duraciones que ofrece el formulario; el backend acepta de 30 a 240 minutos */
export const DURATION_OPTIONS = [30, 45, 60, 90, 120, 180, 240];

export const LEVELS: { value: OfferingLevel; label: string }[] = [
  { value: 'BASICO', label: 'Básico' },
  { value: 'INTERMEDIO', label: 'Intermedio' },
  { value: 'AVANZADO', label: 'Avanzado' }
];

/** Lunes a domingo, en el orden en que se muestran */
export const WEEK_DAYS: { value: WeekDay; label: string; short: string }[] = [
  { value: 'MONDAY', label: 'Lunes', short: 'Lun' },
  { value: 'TUESDAY', label: 'Martes', short: 'Mar' },
  { value: 'WEDNESDAY', label: 'Miércoles', short: 'Mié' },
  { value: 'THURSDAY', label: 'Jueves', short: 'Jue' },
  { value: 'FRIDAY', label: 'Viernes', short: 'Vie' },
  { value: 'SATURDAY', label: 'Sábado', short: 'Sáb' },
  { value: 'SUNDAY', label: 'Domingo', short: 'Dom' }
];

const DAY_ORDER = WEEK_DAYS.map(d => d.value);
// Date.getDay(): 0 = domingo … 6 = sábado
const JS_DAY: WeekDay[] = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

const timeFormat = businessDateFormat({ hour: 'numeric', minute: '2-digit' });
const priceFormat = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

export function levelLabel(level: OfferingLevel | null | undefined): string {
  return LEVELS.find(l => l.value === level)?.label ?? 'Básico';
}

/** 60 → "60 minutos", 90 → "1 h 30 min", 120 → "2 horas" */
export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} minutos`;
  if (minutes === 60) return '60 minutos';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!rest) return `${hours} horas`;
  return `${hours} h ${rest} min`;
}

/** Días ordenados de lunes a domingo, sin repetidos */
export function sortDays(days: readonly WeekDay[]): WeekDay[] {
  return DAY_ORDER.filter(d => days.includes(d));
}

/**
 * Texto corto de los días: "Todos los días", "Lunes a viernes", "Fines de semana"
 * o la lista ("Lunes, miércoles y viernes").
 */
export function formatDays(days: readonly WeekDay[]): string {
  const sorted = sortDays(days);
  if (!sorted.length) return 'Sin días definidos';
  if (sorted.length === 7) return 'Todos los días';
  if (sorted.join() === 'SATURDAY,SUNDAY') return 'Fines de semana';

  // Rango continuo de 3 o más días: "Lunes a viernes"
  const first = DAY_ORDER.indexOf(sorted[0]);
  const continuous = sorted.every((d, i) => DAY_ORDER.indexOf(d) === first + i);
  const labels = sorted.map(d => dayLabel(d).toLowerCase());
  if (continuous && sorted.length >= 3) {
    return capitalize(`${labels[0]} a ${labels[labels.length - 1]}`);
  }
  if (labels.length === 1) return capitalize(labels[0]);
  return capitalize(`${labels.slice(0, -1).join(', ')} y ${labels[labels.length - 1]}`);
}

export function dayLabel(day: WeekDay): string {
  return WEEK_DAYS.find(d => d.value === day)?.label ?? day;
}

/** Día de la semana de una fecha en la zona horaria del navegador */
export function weekDayOf(date: Date): WeekDay {
  return JS_DAY[date.getDay()];
}

/** Hora de finalización: inicio + duración de la sesión */
export function endOf(start: Date | string, durationMinutes: number): Date {
  return new Date(new Date(start).getTime() + durationMinutes * 60_000);
}

export function formatTime(date: Date | string): string {
  return timeFormat.format(new Date(date));
}

/** "9:00 a. m. – 10:30 a. m." */
export function formatTimeRange(start: Date | string, durationMinutes: number): string {
  return `${formatTime(start)} – ${formatTime(endOf(start, durationMinutes))}`;
}

/** Lo que recibe el proveedor después de la comisión de la plataforma */
export function providerEarnings(price: number): number {
  return Math.round(price * (1 - PLATFORM_COMMISSION));
}

export function platformFee(price: number): number {
  return price - providerEarnings(price);
}

export function formatPrice(price: number): string {
  return priceFormat.format(price);
}

const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);
