import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
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

  it('no se sabe si hay sesión hasta que /auth/me responde', () => {
    auth.restore().subscribe();
    expect(auth.ready()).toBeFalse();

    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.ready()).toBeTrue();
  });

  it('pregunta al backend una sola vez aunque varios (arranque y guards) llamen a restore', () => {
    auth.restore().subscribe();
    auth.restore().subscribe();
    http.expectOne('/api/auth/me').flush({ email: 'laura@test.com', name: 'Laura', role: 'PROVIDER' });

    // Quien llega después recibe la respuesta guardada, sin otra petición
    let done = false;
    auth.restore().subscribe({ complete: () => (done = true) });
    expect(done).toBeTrue();
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

  it('logout borra el historial del chat de IA (HU-25) y redirige', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    localStorage.setItem('skillbridge_ai_chat_history', JSON.stringify([{ role: 'user', content: 'privado' }]));
    auth.login('sofia@test.com', 'secreto').subscribe();
    http.expectOne('/api/auth/login').flush({ email: 'sofia@test.com', name: 'Sofía', role: 'CUSTOMER' });

    auth.logout('/login');
    http.expectOne('/api/auth/logout').flush(null);

    expect(localStorage.getItem('skillbridge_ai_chat_history')).toBeNull();
    expect(auth.isAuthenticated()).toBeFalse();
    expect(navigate).toHaveBeenCalledWith('/login');
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

  // El header se pinta completo mientras el backend despierta (Render Free tarda ~1 minuto en /auth/me)
  describe('navRole', () => {
    // El rol guardado se lee al crear el servicio: cada prueba crea uno nuevo después de preparar localStorage
    const KEY = 'skillbridge_last_role';
    const HOUR = 60 * 60 * 1000;
    const freshService = () => new AuthService(TestBed.inject(HttpClient), TestBed.inject(Router));
    const saveLastSession = (role: string, expiresAt: number) =>
      localStorage.setItem(KEY, JSON.stringify({ role, expiresAt }));
    const storedLastSession = () => JSON.parse(localStorage.getItem(KEY) ?? 'null');

    afterEach(() => localStorage.removeItem(KEY));

    it('sin sesión previa en este navegador, pinta al visitante sin esperar a /auth/me', () => {
      localStorage.removeItem(KEY);
      const service = freshService();
      service.restore().subscribe();

      expect(service.ready()).toBeFalse();
      expect(service.navRole()).toBeNull();
      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    it('mientras /auth/me no responde, usa el rol de la última sesión si aún no vence', () => {
      saveLastSession('PROVIDER', Date.now() + HOUR);
      const service = freshService();
      service.restore().subscribe();

      expect(service.navRole()).toBe('PROVIDER');
      // Es solo presentación: la sesión real sigue sin confirmarse
      expect(service.isAuthenticated()).toBeFalse();
      http.expectOne('/api/auth/me').flush({ email: 'laura@test.com', name: 'Laura', role: 'PROVIDER' });
      expect(service.navRole()).toBe('PROVIDER');
    });

    it('si pasaron más de 8 horas desde el login, pinta al visitante y olvida la sesión guardada', () => {
      saveLastSession('CUSTOMER', Date.now() - 1);
      const service = freshService();
      service.restore().subscribe();

      expect(service.navRole()).toBeNull();
      expect(localStorage.getItem(KEY)).toBeNull();
      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    });

    it('si el backend dice que la sesión ya no es válida, pasa a visitante y olvida la sesión guardada', () => {
      saveLastSession('CUSTOMER', Date.now() + HOUR);
      const service = freshService();
      service.restore().subscribe();

      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(service.navRole()).toBeNull();
      expect(localStorage.getItem(KEY)).toBeNull();
    });

    it('un valor manipulado en localStorage no se usa como rol', () => {
      saveLastSession('SUPERADMIN', Date.now() + HOUR);
      expect(freshService().navRole()).toBeNull();

      localStorage.setItem(KEY, 'CUSTOMER');
      expect(freshService().navRole()).toBeNull();
    });

    it('el login guarda el rol con un vencimiento de 8 horas y logout lo borra', () => {
      const before = Date.now();
      auth.login('sofia@test.com', 'secreto').subscribe();
      http.expectOne('/api/auth/login').flush({ email: 'sofia@test.com', name: 'Sofía', role: 'CUSTOMER' });

      const saved = storedLastSession();
      expect(saved.role).toBe('CUSTOMER');
      expect(saved.expiresAt).toBeGreaterThanOrEqual(before + 8 * HOUR);
      expect(saved.expiresAt).toBeLessThanOrEqual(Date.now() + 8 * HOUR);
      // Nada más que el rol y el vencimiento: ni token ni datos personales
      expect(Object.keys(saved).sort()).toEqual(['expiresAt', 'role']);

      auth.logout();
      http.expectOne('/api/auth/logout').flush(null);

      expect(auth.navRole()).toBeNull();
      expect(localStorage.getItem(KEY)).toBeNull();
    });

    it('/auth/me no renueva el vencimiento: la cookie solo se emite al iniciar sesión', () => {
      const expiresAt = Date.now() + 2 * HOUR;
      saveLastSession('CUSTOMER', expiresAt);
      const service = freshService();
      service.restore().subscribe();

      http.expectOne('/api/auth/me').flush({ email: 'sofia@test.com', name: 'Sofía', role: 'CUSTOMER' });

      expect(storedLastSession()).toEqual({ role: 'CUSTOMER', expiresAt });
    });
  });
});
