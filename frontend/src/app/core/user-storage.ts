/**
 * Datos de un usuario que la app guarda en el navegador (HU-29). Se borran al cerrar sesión o al
 * expirar: en un computador compartido, el siguiente usuario no debe ver lo que dejó el anterior.
 * Si una pantalla empieza a guardar algo del usuario, su clave se registra aquí.
 */
export const AI_CHAT_HISTORY_KEY = 'skillbridge_ai_chat_history';
// Formato anterior al chat (HU-25): se sigue borrando por si quedó guardado en algún navegador
export const AI_RECOMMENDATION_KEY = 'skillbridge_ai_last_recommendation';

// Rol de la última sesión: con él se pinta el header mientras el backend despierta (AuthService.navRole)
export const LAST_ROLE_KEY = 'skillbridge_last_role';

const USER_KEYS = [AI_CHAT_HISTORY_KEY, AI_RECOMMENDATION_KEY, LAST_ROLE_KEY];

export function clearUserStorage(): void {
  try {
    USER_KEYS.forEach(key => localStorage.removeItem(key));
  } catch { /* sin storage (modo privado o bloqueado): no hay nada que borrar */ }
}
