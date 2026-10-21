import { BUSINESS_TIME_ZONE, businessDateFormat, businessDayKey } from './business-time';

describe('business-time (BUG-04)', () => {
  // 19/10/2026 23:30 en Colombia = 20/10/2026 04:30 en UTC
  const PAGO_NOCTURNO = '2026-10-20T04:30:00Z';

  it('usa la zona de Colombia', () => {
    expect(BUSINESS_TIME_ZONE).toBe('America/Bogota');
  });

  it('un pago a las 23:30 de Colombia pertenece a ese mismo día', () => {
    expect(businessDayKey(PAGO_NOCTURNO)).toBe('2026-10-19');
  });

  it('muestra la hora de Colombia sin importar la zona del navegador', () => {
    const time = businessDateFormat({ hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(PAGO_NOCTURNO));
    expect(time).toBe('23:30');
  });
});