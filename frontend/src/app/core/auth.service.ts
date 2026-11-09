import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, shareReplay, tap } from 'rxjs';
import { apiBase } from './api';
import { LAST_ROLE_KEY, clearUserStorage } from './user-storage';

export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

/** Usuario de la sesión, tal como lo devuelve el backend. Nunca incluye el token. */
export interface SessionUser { email: string; name: string; role: Role; }
export interface ProviderRegistration { status: string; message: string; }

// Clave de la versión anterior, cuando el JWT se guardaba en el navegador
const LEGACY_TOKEN_KEY = 'skillbridge_token';
const ROLES: readonly Role[] = ['CUSTOMER', 'PROVIDER', 'ADMIN'];
// Lo que dura la cookie de sesión: debe coincidir con app.jwt.expiration del backend (8 horas)
export const SESSION_DURATION_MS = 8 * 60 * 60 * 1000;

/** Rol de la última sesión y cuándo vence esa sesión. Nunca el token ni datos personales. */
interface LastSession { role: Role; expiresAt: number; }

// Una sesión vencida o un valor manipulado cuentan como "no hubo sesión": se pinta al visitante
function readLastSession(): LastSession | null {
  try {
    const value = JSON.parse(localStorage.getItem(LAST_ROLE_KEY) ?? 'null') as Partial<LastSession> | null;
    if (value && ROLES.includes(value.role as Role) && typeof value.expiresAt === 'number' && value.expiresAt > Date.now()) {
      return { role: value.role as Role, expiresAt: value.expiresAt };
    }
    localStorage.removeItem(LAST_ROLE_KEY);
  } catch { /* sin storage o JSON inválido */ }
  return null;
}

function writeLastSession(session: LastSession): void {
  try { localStorage.setItem(LAST_ROLE_KEY, JSON.stringify(session)); } catch { /* sin storage: se pinta como visitante */ }
}

/**
 * Sesión del usuario (HU-29). El JWT viaja en una cookie HttpOnly que el JavaScript no puede leer:
 * aquí solo se guarda en memoria quién es el usuario, y al arrancar se le pregunta al backend (/auth/me).
 * Esa pregunta no bloquea el arranque: en Render Free el backend puede tardar ~1 minuto en despertar y la
 * página quedaría en blanco. Solo los guards esperan la respuesta.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly user = signal<SessionUser | null>(null);
  private readonly checked = signal(false);
  private restoring?: Observable<void>;
  // Última sesión en este navegador, si aún no vence. Solo sirve para pintar el header mientras /auth/me responde
  private readonly lastSession = signal<LastSession | null>(readLastSession());

  /** false mientras /auth/me no ha respondido: todavía no se sabe si hay sesión. */
  readonly ready = this.checked.asReadonly();

  readonly authenticated = computed(() => this.user() !== null);
  readonly role = computed(() => this.user()?.role ?? null);

  /**
   * Rol con el que se pinta la navegación. Con el backend dormido /auth/me puede tardar ~1 minuto y el header
   * no puede quedar a medias: mientras tanto se usa el rol de la última sesión si aún no vence (8 horas desde
   * el login), o visitante (null) si no la hubo o ya venció.
   * Es solo presentación: los guards esperan la respuesta real y el backend autoriza cada petición.
   */
  readonly navRole = computed<Role | null>(() =>
    this.user()?.role ?? (this.checked() ? null : this.lastSession()?.role ?? null));
  readonly email = computed(() => this.user()?.email ?? null);
  readonly name = computed(() => this.user()?.name ?? null);

  constructor(private http: HttpClient, private router: Router) {}

  /**
   * Recupera la sesión si la cookie sigue vigente. Se pide al backend una sola vez: las llamadas
   * siguientes (los guards) reciben la misma respuesta, o la esperan si aún no ha llegado.
   */
  restore(): Observable<void> {
    this.restoring ??= this.fetchSession().pipe(
      // Siempre emite una vez (el error ya se convirtió en visitante): a partir de aquí se sabe si hay sesión
      tap(() => this.checked.set(true)),
      shareReplay(1)
    );
    return this.restoring;
  }

  private fetchSession(): Observable<void> {
    try { localStorage.removeItem(LEGACY_TOKEN_KEY); } catch { /* sin storage */ }
    return this.http.get<SessionUser>(`${apiBase()}/auth/me`).pipe(
      tap(user => this.setUser(user, false)),
      // 401 (sin cookie o expirada) o backend caído: se arranca como visitante, sin datos del usuario anterior
      catchError(() => { this.clearSession(); return of(null); }),
      map(() => undefined)
    );
  }

  login(email: string, password: string): Observable<SessionUser> {
    return this.http.post<SessionUser>(`${apiBase()}/auth/login`, { email, password })
      .pipe(tap(user => this.setUser(user, true)));
  }

  register(name: string, email: string, password: string): Observable<SessionUser> {
    return this.http.post<SessionUser>(`${apiBase()}/auth/register`, { name, email, password })
      .pipe(tap(user => this.setUser(user, true)));
  }

  /**
   * Solo se recuerda el rol y cuándo vence la sesión (nunca el token ni el email): la próxima visita pinta el
   * header completo de inmediato. El vencimiento se fija al iniciar sesión, que es cuando el backend emite la
   * cookie; /auth/me no la renueva, así que ahí solo se actualiza el rol y se conserva el vencimiento.
   */
  private setUser(user: SessionUser, startsSession: boolean): void {
    this.user.set(user);
    const previous = this.lastSession();
    const expiresAt = startsSession ? Date.now() + SESSION_DURATION_MS : previous?.expiresAt;
    // Sin vencimiento conocido (p. ej. storage borrado) no se adivina: la próxima visita se pinta como visitante
    if (expiresAt === undefined) return;
    const session = { role: user.role, expiresAt };
    this.lastSession.set(session);
    writeLastSession(session);
  }

  private clearSession(): void {
    this.user.set(null);
    this.lastSession.set(null);
    clearUserStorage();
  }

  // El proveedor queda pendiente de aprobación: el backend no abre sesión
  registerProvider(name: string, email: string, password: string) {
    return this.http.post<ProviderRegistration>(`${apiBase()}/auth/register/provider`, { name, email, password });
  }

  // La cookie expira sola en el navegador y, si el backend la rechaza, el interceptor cierra la sesión
  isAuthenticated(): boolean { return this.authenticated(); }

  hasRole(...roles: Role[]): boolean {
    return this.isAuthenticated() && roles.includes(this.role() as Role);
  }

  /**
   * La cookie es HttpOnly: solo el backend puede borrarla. La sesión local se limpia al instante para que
   * la pantalla responda enseguida, aunque la petición falle (por ejemplo, sin conexión).
   */
  logout(redirectTo = '/'): void {
    // En un computador compartido, el siguiente usuario no debe ver lo que dejó este (p. ej. la recomendación de IA)
    this.clearSession();
    this.http.post<void>(`${apiBase()}/auth/logout`, {}).subscribe({ error: () => {} });
    this.router.navigateByUrl(redirectTo);
  }
}
