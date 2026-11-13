import { isDevMode } from '@angular/core';

const MOCK_KEY = 'skillbridge_verification_mock';

/**
 * Verificación de correo simulada (HU #39) para trabajar sin el email-service. Solo existe en desarrollo
 * (ng serve) y está apagada por defecto, igual que el mock del panel de admin: si un compañero tiene el
 * email-service real arrancado, no debe responderle el mock sin que se dé cuenta.
 * Encender desde la consola del navegador: localStorage.setItem('skillbridge_verification_mock', 'on')
 */
export function verificationMockEnabled(): boolean {
  if (!isDevMode()) return false;
  try { return localStorage.getItem(MOCK_KEY) === 'on'; } catch { return false; }
}
