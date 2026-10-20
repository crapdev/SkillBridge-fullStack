import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { apiBase } from './api';
import { clearUserStorage } from './user-storage';

export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

/** Usuario de la sesión, tal como lo devuelve el backend. Nunca incluye el token. */
export interface SessionUser { email: string; name: string; role: Role; }
export interface ProviderRegistration { status: string; message: string; }

// Clave de la versión anterior, cuando el JWT se guardaba en el navegador
const LEGACY_TOKEN_KEY = 'skillbridge_token';

/**
 * Sesión del usuario (HU-29). El JWT viaja en una cookie HttpOnly que el JavaScript no puede leer:
 * aquí solo se guarda en memoria quién es el usuario, y al arrancar se le pregunta al backend (/auth/me).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly user = signal<SessionUser | null>(null);

  readonly authenticated = computed(() => this.user() !== null);
  readonly role = computed(() => this.user()?.role ?? null);
  readonly email = computed(() => this.user()?.email ?? null);
  readonly name = computed(() => this.user()?.name ?? null);

  constructor(private http: HttpClient, private router: Router) {}

  /** Se ejecuta una vez al arrancar la app: si la cookie sigue vigente, recupera la sesión. */
  restore(): Observable<void> {
    try { localStorage.removeItem(LEGACY_TOKEN_KEY); } catch { /* sin storage */ }
    return this.http.get<SessionUser>(`${apiBase()}/auth/me`).pipe(
      tap(user => this.user.set(user)),
      // 401 (sin cookie o expirada) o backend caído: se arranca como visitante, sin datos del usuario anterior
      catchError(() => { this.user.set(null); clearUserStorage(); return of(null); }),
      map(() => undefined)
    );
  }

  login(email: string, password: string): Observable<SessionUser> {
    return this.http.post<SessionUser>(`${apiBase()}/auth/login`, { email, password })
      .pipe(tap(user => this.user.set(user)));
  }

  register(name: string, email: string, password: string): Observable<SessionUser> {
    return this.http.post<SessionUser>(`${apiBase()}/auth/register`, { name, email, password })
      .pipe(tap(user => this.user.set(user)));
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
    this.user.set(null);
    // En un computador compartido, el siguiente usuario no debe ver lo que dejó este (p. ej. la recomendación de IA)
    clearUserStorage();
    this.http.post<void>(`${apiBase()}/auth/logout`, {}).subscribe({ error: () => {} });
    this.router.navigateByUrl(redirectTo);
  }
}
