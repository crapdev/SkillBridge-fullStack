import {
  PLATFORM_COMMISSION, endOf, formatDays, formatDuration, formatTimeRange, levelLabel, platformFee,
  providerEarnings, sortDays, weekDayOf
} from './offering-format';

/** SCRUM-175 (HU-20): textos y reglas de la ficha de mentoría */
describe('offering-format', () => {

  it('la comisión de la plataforma es del 10% y el proveedor recibe el resto', () => {
    expect(PLATFORM_COMMISSION).toBe(0.10);
    expect(providerEarnings(85000)).toBe(76500);
    expect(platformFee(85000)).toBe(8500);
    expect(providerEarnings(85000) + platformFee(85000)).toBe(85000);
  });

  it('formatea la duración de la sesión', () => {
    expect(formatDuration(30)).toBe('30 minutos');
    expect(formatDuration(60)).toBe('60 minutos');
    expect(formatDuration(90)).toBe('1 h 30 min');
    expect(formatDuration(120)).toBe('2 horas');
  });

  it('resume los días de la semana', () => {
    expect(formatDays(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'])).toBe('Lunes a viernes');
    expect(formatDays(['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'])).toBe('Todos los días');
    expect(formatDays(['SUNDAY', 'SATURDAY'])).toBe('Fines de semana');
    expect(formatDays(['FRIDAY', 'MONDAY', 'WEDNESDAY'])).toBe('Lunes, miércoles y viernes');
    expect(formatDays(['TUESDAY'])).toBe('Martes');
    expect(formatDays([])).toBe('Sin días definidos');
  });

  it('ordena los días de lunes a domingo sin repetidos', () => {
    expect(sortDays(['SUNDAY', 'MONDAY', 'MONDAY'])).toEqual(['MONDAY', 'SUNDAY']);
  });

  it('obtiene el día de la semana de una fecha local', () => {
    expect(weekDayOf(new Date(2026, 9, 12))).toBe('MONDAY');   // 12 de octubre de 2026
    expect(weekDayOf(new Date(2026, 9, 18))).toBe('SUNDAY');
  });

  it('calcula la hora de finalización con la duración', () => {
    const start = new Date(2026, 9, 12, 9, 0);
    expect(endOf(start, 90)).toEqual(new Date(2026, 9, 12, 10, 30));

    const range = formatTimeRange(start, 90);
    expect(range).toContain('9:00');
    expect(range).toContain('10:30');
    expect(range).toContain('–');
  });

  it('traduce el nivel', () => {
    expect(levelLabel('AVANZADO')).toBe('Avanzado');
    expect(levelLabel(undefined)).toBe('Básico');
  });
});
