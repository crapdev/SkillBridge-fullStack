import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService, Role } from './auth.service';
import { customerGuard } from './role.guard';

describe('customerGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });

  // La sesión vive en memoria (HU-29): se abre simulando la respuesta del backend al login
  function signIn(role: Role): void {
    TestBed.inject(AuthService).login('a@a.com', 'secreto').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({ email: 'a@a.com', name: 'Ana', role });
  }

  function run(url = '/book'): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      customerGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot)) as boolean | UrlTree;
  }

  const serialize = (result: boolean | UrlTree) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('redirige a /login conservando la URL pedida cuando no hay sesión', () => {
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
    TestBed.inject(HttpTestingController).expectOne('/api/auth/logout').flush(null);

    expect(serialize(run('/ai'))).toBe('/login?returnUrl=%2Fai');
  });
});
