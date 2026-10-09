import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { apiBase } from './api';

interface AuthResponse { token: string; tokenType: string; }
export interface ProviderRegistration { status: string; message: string; }
interface JwtPayload { sub?: string; role?: string; exp?: number; }

export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly key = 'skillbridge_token';
  private readonly payload = signal<JwtPayload | null>(this.readValidPayload());

  readonly authenticated = computed(() => this.payload() !== null);
  readonly role = computed(() => (this.payload()?.role ?? null) as Role | null);

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/login`, { email, password })
      .pipe(tap(r => this.save(r.token)));
  }

  register(name: string, email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/register`, { name, email, password })
      .pipe(tap(r => this.save(r.token)));
  }

  // El proveedor queda pendiente de aprobación: el backend no devuelve token, así que no se inicia sesión
  registerProvider(name: string, email: string, password: string) {
    return this.http.post<ProviderRegistration>(`${apiBase()}/auth/register/provider`, { name, email, password });
  }

  token(): string | null { return localStorage.getItem(this.key); }

  // El token puede expirar con la app abierta, por eso se vuelve a comprobar `exp` en cada consulta
  isAuthenticated(): boolean {
    const payload = this.payload();
    if (payload && AuthService.isExpired(payload)) {
      this.clear();
      return false;
    }
    return payload !== null;
  }

  hasRole(...roles: Role[]): boolean {
    return this.isAuthenticated() && roles.includes(this.role() as Role);
  }

  logout(redirectTo = '/'): void { this.clear(); this.router.navigateByUrl(redirectTo); }

  private save(token: string): void {
    localStorage.setItem(this.key, token);
    this.payload.set(AuthService.decode(token));
  }

  private clear(): void { localStorage.removeItem(this.key); this.payload.set(null); }

  private readValidPayload(): JwtPayload | null {
    const token = localStorage.getItem(this.key);
    const payload = token ? AuthService.decode(token) : null;
    if (!payload || AuthService.isExpired(payload)) {
      localStorage.removeItem(this.key);
      return null;
    }
    return payload;
  }

  // Solo lee el payload para la UX (mostrar u ocultar rutas); la firma la valida siempre el backend
  private static decode(token: string): JwtPayload | null {
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=')));
    } catch {
      return null;
    }
  }

  private static isExpired(payload: JwtPayload): boolean {
    return payload.exp !== undefined && payload.exp * 1000 <= Date.now();
  }
}
