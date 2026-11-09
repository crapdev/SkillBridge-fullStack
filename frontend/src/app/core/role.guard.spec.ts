import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService, Role } from './auth.service';
import { customerGuard } from './role.guard';

describe('customerGuard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  // Arranque como visitante: /auth/me responde 401
  function startAsVisitor(): void {
    TestBed.inject(AuthService).restore().subscribe();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
  }

  // La sesión vive en memoria (HU-29): se abre simulando la respuesta del backend al login
  function signIn(role: Role): void {
    startAsVisitor();
    TestBed.inject(AuthService).login('a@a.com', 'secreto').subscribe();
    http.expectOne('/api/auth/login').flush({ email: 'a@a.com', name: 'Ana', role });
  }

  // El guard devuelve un Observable: se guarda lo que emite (undefined mientras espera a /auth/me)
  function run(url = '/book'): boolean | UrlTree | undefined {
    let result: boolean | UrlTree | undefined;
    TestBed.runInInjectionContext(() =>
      (customerGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as Observable<boolean | UrlTree>)
        .subscribe(r => (result = r)));
    return result;
  }

  const serialize = (result: boolean | UrlTree | undefined) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('redirige a /login conservando la URL pedida cuando no hay sesión', () => {
    startAsVisitor();
    expect(serialize(run('/book'))).toBe('/login?returnUrl=%2Fbook');
  });

  it('permite el acceso a un CUSTOMER con sesión', () => {
    signIn('CUSTOMER');
    expect(run()).toBeTrue();
  });

  it('redirige al inicio a un usuario autenticado con otro rol', () => {
    signIn('PROVIDER');
    expect(serialize(run())).toBe('/');
  });

  it('deja de permitir el acceso después de cerrar sesión', () => {
    signIn('CUSTOMER');
    TestBed.inject(AuthService).logout();
    http.expectOne('/api/auth/logout').flush(null);

    expect(serialize(run('/ai'))).toBe('/login?returnUrl=%2Fai');
  });

  it('al recargar espera a /auth/me antes de decidir: no manda al login a quien tiene la cookie vigente', () => {
    let result: boolean | UrlTree | undefined;
    TestBed.runInInjectionContext(() =>
      (customerGuard({} as ActivatedRouteSnapshot, { url: '/ai' } as RouterStateSnapshot) as Observable<boolean | UrlTree>)
        .subscribe(r => (result = r)));

    // El backend aún no responde (cold start): el guard no ha decidido
    expect(result).toBeUndefined();

    http.expectOne('/api/auth/me').flush({ email: 'a@a.com', name: 'Ana', role: 'CUSTOMER' });
    expect(result).toBeTrue();
  });
});
