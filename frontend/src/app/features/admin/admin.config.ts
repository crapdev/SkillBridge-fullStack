import { isDevMode } from '@angular/core';

const MOCK_KEY = 'skillbridge_admin_mock';

/**
 * Datos simulados para ver el panel sin backend. Solo existen en desarrollo (ng serve) y están
 * apagados por defecto; un build de producción nunca los usa.
 */
export function adminMockEnabled(): boolean {
  if (!isDevMode()) return false;
  try { return localStorage.getItem(MOCK_KEY) === 'on'; } catch { return false; }
}

export function setAdminMock(on: boolean): void {
  try { on ? localStorage.setItem(MOCK_KEY, 'on') : localStorage.removeItem(MOCK_KEY); } catch { /* sin storage */ }
}
