import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService (sesión en cookie HttpOnly, HU-29)', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('recupera la sesión al arrancar si la cookie sigue vigente', () => {
    auth.restore().subscribe();
    http.expectOne('/api/auth/me').flush({ email: 'laura@test.com', name: 'Laura', role: 'PROVIDER' });

    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.hasRole('PROVIDER')).toBeTrue();
    expect(auth.name()).toBe('Laura');
  });

  it('arranca como visitante si el backend responde 401', () => {
    let done = false;
    auth.restore().subscribe({ complete: () => (done = true) });
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    // El error no rompe el arranque de la app
    expect(done).toBeTrue();
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('borra el token que guardaba la versión anterior en localStorage', () => {
    localStorage.setItem('skillbridge_token', 'token-viejo');

    auth.restore().subscribe();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(localStorage.getItem('skillbridge_token')).toBeNull();
  });

  it('el login guarda el usuario en memoria y nada en localStorage', () => {
    auth.login('sofia@test.com', 'secreto').subscribe();
    http.expectOne('/api/auth/login').flush({ email: 'sofia@test.com', name: 'Sofía', role: 'CUSTOMER' });

    expect(auth.hasRole('CUSTOMER')).toBeTrue();
    expect(localStorage.getItem('skillbridge_token')).toBeNull();
  });

  it('logout borra la recomendación de IA guardada para que el siguiente usuario no la vea', () => {
    localStorage.setItem('skillbridge_ai_last_recommendation', '{"goal":"privado","answer":"…"}');
    auth.login('sofia@test.com', 'secreto').subscribe();
    http.expectOne('/api/auth/login').flush({ email: 'sofia@test.com', name: 'Sofía', role: 'CUSTOMER' });

    auth.logout();
    http.expectOne('/api/auth/logout').flush(null);

    expect(localStorage.getItem('skillbridge_ai_last_recommendation')).toBeNull();
  });

  it('al arrancar con la sesión expirada también borra los datos del usuario anterior', () => {
    localStorage.setItem('skillbridge_ai_last_recommendation', '{"goal":"privado","answer":"…"}');

    auth.restore().subscribe();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(localStorage.getItem('skillbridge_ai_last_recommendation')).toBeNull();
  });

  it('logout limpia la sesión al instante y le pide al backend borrar la cookie', () => {
    auth.login('sofia@test.com', 'secreto').subscribe();
    http.expectOne('/api/auth/login').flush({ email: 'sofia@test.com', name: 'Sofía', role: 'CUSTOMER' });

    auth.logout();

    expect(auth.isAuthenticated()).toBeFalse();
    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
