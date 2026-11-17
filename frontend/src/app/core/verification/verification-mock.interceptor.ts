import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, delay, of, switchMap, throwError, timer } from 'rxjs';
import { verificationMockEnabled } from './verification.config';

/**
 * email-service simulado para /api/verification (HU #39). Implementa exactamente el contrato acordado con
 * el backend (mismos códigos y campos), así las pantallas no cambian al conectar el servicio real.
 * Los datos viven en memoria y se reinician al recargar la página.
 *
 * Tokens fijos para probar cada caso en /verificar-correo?token=...
 *   token-valido     → cliente verificado (200 + usuario)
 *   token-proveedor  → proveedor pendiente de aprobación (202)
 *   token-vencido    → 400 TOKEN_EXPIRED
 *   token-usado      → 400 TOKEN_USED
 *   cualquier otro   → 400 TOKEN_INVALID
 * Al registrarse, el "correo" con el enlace se escribe en la consola del navegador.
 */
export const verificationMockInterceptor: HttpInterceptorFn = (req, next) => {
  const match = req.url.match(/\/verification\/(register\/provider|register|verify|resend|status)$/);
  if (!match || !verificationMockEnabled()) return next(req);
  if (req.method !== 'POST') return fail(405, 'Método no soportado');
  return handle(match[1], req);
};

type Role = 'CUSTOMER' | 'PROVIDER';
interface Pending { name: string; email: string; password: string; role: Role; token: string; expiresAt: number; }

const LINK_TTL_MS = 24 * 60 * 60 * 1000;
const RESEND_LIMIT = 3;
const RESEND_WINDOW_MS = 15 * 60 * 1000;
const SENT_MESSAGE = 'Te enviamos un enlace de verificación. Revisa tu correo para activar tu cuenta.';

// Correos que ya están en app_users: registrarse con ellos da 422
const registered = new Set(['existente@skillbridge.dev']);
// Registros sin verificar, por correo (en el backend real: email.pending_registrations)
const pending = new Map<string, Pending>();
const usedTokens = new Set<string>();
// Momentos en que se pidió reenviar el enlace, por correo
const resends = new Map<string, number[]>();

function handle(action: string, req: HttpRequest<unknown>): Observable<HttpResponse<unknown>> {
  const body = (req.body ?? {}) as Record<string, unknown>;
  switch (action) {
    case 'register': return register(body, 'CUSTOMER');
    case 'register/provider': return register(body, 'PROVIDER');
    case 'verify': return verify(String(body['token'] ?? ''));
    case 'resend': return resend(normalize(body['email']));
    default: return status(normalize(body['email']), String(body['password'] ?? ''));
  }
}

function register(body: Record<string, unknown>, role: Role) {
  const name = String(body['name'] ?? '').trim();
  const email = normalize(body['email']);
  const password = String(body['password'] ?? '');
  if (!name || name.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || password.length < 8 || password.length > 72) {
    return fail(400, 'Revisa los datos del formulario.');
  }
  if (registered.has(email)) return fail(422, 'El correo ya está registrado');
  // Un correo que ya estaba pendiente se reemplaza: el enlace anterior deja de valer
  pending.set(email, { name, email, password, role, token: '', expiresAt: 0 });
  sendLink(email);
  return ok({ email, message: SENT_MESSAGE }, 202);
}

function verify(token: string) {
  if (token === 'token-vencido') return reject('TOKEN_EXPIRED', 'El enlace de verificación venció.');
  if (token === 'token-usado' || usedTokens.has(token)) return reject('TOKEN_USED', 'El enlace ya fue usado.');
  if (token === 'token-valido') return ok({ email: 'cliente@skillbridge.dev', name: 'Cliente de prueba', role: 'CUSTOMER' });
  if (token === 'token-proveedor') return providerPending();

  const entry = [...pending.values()].find(p => p.token === token);
  if (!entry) return reject('TOKEN_INVALID', 'El enlace de verificación no es válido.');
  if (entry.expiresAt < Date.now()) return reject('TOKEN_EXPIRED', 'El enlace de verificación venció.');

  // Verificado: pasa de pendiente a usuario (el backend real además emite la cookie sb_token al cliente)
  pending.delete(entry.email);
  usedTokens.add(token);
  registered.add(entry.email);
  return entry.role === 'CUSTOMER'
    ? ok({ email: entry.email, name: entry.name, role: entry.role })
    : providerPending();
}

function resend(email: string) {
  // Se cuentan también los correos que no existen: si no, el 429 revelaría cuáles están pendientes
  const now = Date.now();
  const recent = (resends.get(email) ?? []).filter(t => now - t < RESEND_WINDOW_MS);
  if (recent.length >= RESEND_LIMIT) return fail(429, 'Pediste demasiados enlaces. Espera unos minutos e inténtalo de nuevo.');
  resends.set(email, [...recent, now]);
  if (pending.has(email)) sendLink(email);
  // Siempre la misma respuesta, exista o no el correo
  return ok({ message: SENT_MESSAGE }, 202);
}

// Pide la contraseña para que nadie pueda averiguar qué correos están pendientes
function status(email: string, password: string) {
  return ok({ pending: pending.get(email)?.password === password });
}

const providerPending = () => ok({
  status: 'PENDING_APPROVAL',
  message: 'Correo verificado. Un administrador revisará tu perfil y podrás iniciar sesión cuando lo apruebe.'
}, 202);

// Genera un enlace nuevo (invalida el anterior) y lo "envía": aquí no hay correo, se escribe en la consola.
// Solo el mock lo hace; el backend real nunca debe registrar el enlace en los logs
function sendLink(email: string): void {
  const entry = pending.get(email)!;
  entry.token = 'mock-' + crypto.randomUUID();
  entry.expiresAt = Date.now() + LINK_TTL_MS;
  console.info(`[mock verificación] Correo para ${email}: ${location.origin}/verificar-correo?token=${entry.token}`);
}

const normalize = (value: unknown) => String(value ?? '').trim().toLowerCase();

const ok = (body: unknown, status = 200) => of(new HttpResponse({ status, body })).pipe(delay(350));
// Mismo formato ProblemDetail que el resto del backend (delay() no retrasa errores, por eso timer)
const fail = (status: number, detail: string, reason?: string) =>
  timer(350).pipe(switchMap(() => throwError(() =>
    new HttpErrorResponse({ status, error: { status, detail, ...(reason ? { reason } : {}) } }))));
const reject = (reason: string, detail: string) => fail(400, detail, reason);
