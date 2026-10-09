import { isDevMode } from '@angular/core';

/**
 * Activar/desactivar usuarios todavía no tiene respaldo en la base de datos (falta app_users.active
 * y PATCH /api/admin/users/{id}/status). Cuando el backend lo implemente basta con poner esto en true.
 */
export const USER_STATUS_ENABLED = false;

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

/** Con datos simulados el flujo de estados funciona completo para poder revisarlo. */
export const userStatusEnabled = () => USER_STATUS_ENABLED || adminMockEnabled();
