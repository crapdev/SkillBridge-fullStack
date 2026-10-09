import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { customerGuard } from './role.guard';

const TOKEN_KEY = 'skillbridge_token';

/** Construye un JWT sin firma válida: el frontend solo lee el payload. */
function fakeJwt(payload: object): string {
  const encode = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.firma`;
}

const inOneHour = () => Math.floor(Date.now() / 1000) + 3600;

describe('customerGuard', () => {
  function setup(token?: string) {
    localStorage.removeItem(TOKEN_KEY);
    if (token) localStorage.setItem(TOKEN_KEY, token);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient()] });
  }

  function run(url = '/book'): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      customerGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot)) as boolean | UrlTree;
  }

  const serialize = (result: boolean | UrlTree) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  afterEach(() => localStorage.removeItem(TOKEN_KEY));

  it('redirige a /login conservando la URL pedida cuando no hay sesión', () => {
    setup();
    expect(serialize(run('/book'))).toBe('/login?returnUrl=%2Fbook');
  });

  it('redirige a /login y limpia el token cuando está expirado', () => {
    setup(fakeJwt({ sub: 'a@a.com', role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) - 10 }));
    expect(serialize(run('/ai'))).toBe('/login?returnUrl=%2Fai');
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('redirige a /login cuando el token está malformado', () => {
    setup('esto-no-es-un-jwt');
    expect(serialize(run())).toBe('/login?returnUrl=%2Fbook');
  });

  it('permite el acceso a un CUSTOMER con token vigente', () => {
    setup(fakeJwt({ sub: 'a@a.com', role: 'CUSTOMER', exp: inOneHour() }));
    expect(run()).toBeTrue();
  });

  it('redirige al inicio a un usuario autenticado con otro rol', () => {
    setup(fakeJwt({ sub: 'p@a.com', role: 'PROVIDER', exp: inOneHour() }));
    expect(serialize(run())).toBe('/');
  });

  it('deja de permitir el acceso si el token expira con la app abierta', () => {
    setup(fakeJwt({ sub: 'a@a.com', role: 'CUSTOMER', exp: inOneHour() }));
    expect(run()).toBeTrue();

    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(Date.now() + 2 * 3600 * 1000));
    try {
      expect(serialize(run())).toBe('/login?returnUrl=%2Fbook');
      expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
