import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NgForm } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LoginComponent, RESEND_COOLDOWN_S } from './login.component';

describe('LoginComponent (aviso de sesión expirada)', () => {
  function render(params: Record<string, string>): ComponentFixture<LoginComponent> {
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(params) } } }
      ]
    });
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    return fixture;
  }

  const dialog = (f: ComponentFixture<LoginComponent>) =>
    (f.nativeElement as HTMLElement).querySelector('[role="alertdialog"]');

  it('abre una ventana explicando que la sesión expiró cuando llega con ?expired=1', () => {
    const fixture = render({ expired: '1', returnUrl: '/provider' });
    expect(dialog(fixture)?.textContent).toContain('Tu sesión expiró');
  });

  it('cierra la ventana con "Entendido" y deja el formulario de ingreso', () => {
    const fixture = render({ expired: '1' });
    (dialog(fixture)?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(dialog(fixture)).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).not.toBeNull();
  });

  it('no muestra la ventana en un ingreso normal', () => {
    expect(dialog(render({}))).toBeNull();
  });
});

describe('LoginComponent (verificación de correo, HU #39)', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let http: HttpTestingController;
  let navigate: jasmine.Spy;
  const el = () => fixture.nativeElement as HTMLElement;
  // Formulario válido simulado: la validación de campos no es lo que se prueba aquí
  const validForm = { invalid: false, control: { markAllAsTouched() {} } } as unknown as NgForm;
  const accepted = { status: 202, statusText: 'Accepted' };
  const unauthorized = { status: 401, statusText: 'Unauthorized' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } }
      ]
    });
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    fixture.detectChanges();
  });

  afterEach(() => { fixture.destroy(); http.verify(); });

  function fill(mode: 'login' | 'register', type: 'CUSTOMER' | 'PROVIDER' = 'CUSTOMER'): void {
    component.mode = mode;
    component.accountType = type;
    component.name = 'Ana'; component.email = 'ana@test.com'; component.password = 'secreto123';
  }

  function loginWith401(): void {
    fill('login');
    component.submit(validForm);
    http.expectOne('/api/auth/login').flush({ detail: 'Credenciales inválidas' }, unauthorized);
  }

  describe('registro', () => {
    it('cliente: muestra "Revisa tu correo" con el email devuelto, sin navegar y con cuenta atrás en 60', fakeAsync(() => {
      fill('register');
      component.submit(validForm);
      http.expectOne('/api/verification/register').flush({ email: 'ana@test.com', message: 'Enviado' }, accepted);
      fixture.detectChanges();

      expect(el().textContent).toContain('Revisa tu correo');
      expect(el().textContent).toContain('ana@test.com');
      expect(el().textContent).not.toContain('un administrador revisará');
      expect(navigate).not.toHaveBeenCalled();
      expect(RESEND_COOLDOWN_S).toBe(60);
      expect(component.resendCooldown).toBe(RESEND_COOLDOWN_S);
      discardPeriodicTasks();
    }));

    it('proveedor: misma pantalla con el aviso de revisión del administrador', fakeAsync(() => {
      fill('register', 'PROVIDER');
      component.submit(validForm);
      http.expectOne('/api/verification/register/provider').flush({ email: 'ana@test.com', message: 'Enviado' }, accepted);
      fixture.detectChanges();

      expect(el().textContent).toContain('Revisa tu correo');
      expect(el().textContent).toContain('un administrador revisará tu perfil');
      discardPeriodicTasks();
    }));

    it('con error 422 muestra el detail del backend', () => {
      fill('register');
      component.submit(validForm);
      http.expectOne('/api/verification/register')
        .flush({ detail: 'Ese correo ya está registrado' }, { status: 422, statusText: 'Unprocessable Entity' });
      fixture.detectChanges();

      expect(component.sent).toBeNull();
      expect(el().querySelector('.alert')?.textContent).toContain('Ese correo ya está registrado');
    });
  });

  describe('login con cuenta sin verificar', () => {
    it('401 y pending:true muestra el aviso de correo no verificado y no el error normal', () => {
      loginWith401();
      http.expectOne('/api/verification/status').flush({ pending: true });
      fixture.detectChanges();

      expect(el().textContent).toContain('Tu correo no está verificado');
      expect(component.error).toBe('');
      expect(el().textContent).not.toContain('Credenciales inválidas');
    });

    it('401 y pending:false muestra el error normal', () => {
      loginWith401();
      http.expectOne('/api/verification/status').flush({ pending: false });
      fixture.detectChanges();

      expect(component.unverified).toBeFalse();
      expect(el().textContent).not.toContain('Tu correo no está verificado');
      expect(el().querySelector('.alert')?.textContent).toContain('Credenciales inválidas');
    });

    it('401 sin detail usa el texto por defecto', () => {
      fill('login');
      component.submit(validForm);
      http.expectOne('/api/auth/login').flush(null, unauthorized);
      http.expectOne('/api/verification/status').flush({ pending: false });

      expect(component.error).toBe('No fue posible autenticar. Inténtalo de nuevo.');
    });

    it('401 y status con error de red muestra el error normal', () => {
      loginWith401();
      http.expectOne('/api/verification/status').error(new ProgressEvent('error'));
      fixture.detectChanges();

      expect(component.unverified).toBeFalse();
      expect(component.loading).toBeFalse();
      expect(el().querySelector('.alert')?.textContent).toContain('Credenciales inválidas');
    });

    it('un error distinto de 401 no consulta /status', () => {
      fill('login');
      component.submit(validForm);
      http.expectOne('/api/auth/login').flush({ detail: 'Fallo interno' }, { status: 500, statusText: 'Server Error' });

      http.expectNone('/api/verification/status');
      expect(component.error).toBe('Fallo interno');
      expect(component.loading).toBeFalse();
    });
  });

  describe('reenvío del enlace', () => {
    function showSent(): void {
      fill('register');
      component.submit(validForm);
      http.expectOne('/api/verification/register').flush({ email: 'ana@test.com', message: 'Enviado' }, accepted);
    }

    it('no reenvía mientras la cuenta atrás siga activa', fakeAsync(() => {
      const calls = spyOn(TestBed.inject(AuthService), 'resendVerification');
      showSent();
      component.resend();

      http.expectNone('/api/verification/resend');
      expect(calls).not.toHaveBeenCalled();
      discardPeriodicTasks();
    }));

    it('la cuenta atrás baja cada segundo y llega a 0 a los 60 s', fakeAsync(() => {
      showSent();
      tick(1000);
      expect(component.resendCooldown).toBe(59);
      tick(58000);
      expect(component.resendCooldown).toBe(1);
      tick(1000);
      expect(component.resendCooldown).toBe(0);
      // Al llegar a 0 el intervalo se limpia: no queda ningún timer pendiente
    }));

    it('éxito: muestra el aviso y reinicia la cuenta atrás en 60', fakeAsync(() => {
      showSent();
      tick(60000);
      component.resend();
      const req = http.expectOne('/api/verification/resend');
      expect(req.request.body).toEqual({ email: 'ana@test.com' });
      req.flush({ message: 'ok' }, accepted);
      fixture.detectChanges();

      expect(component.resendNotice).toContain('enlace nuevo');
      expect(component.resendCooldown).toBe(60);
      expect(el().textContent).toContain('Reenviar en 60 s');
      discardPeriodicTasks();
    }));

    it('429 muestra el detail del backend', fakeAsync(() => {
      showSent();
      tick(60000);
      component.resend();
      http.expectOne('/api/verification/resend')
        .flush({ detail: 'Demasiados intentos' }, { status: 429, statusText: 'Too Many Requests' });
      fixture.detectChanges();

      expect(component.resendError).toBe('Demasiados intentos');
      expect(el().textContent).toContain('Demasiados intentos');
    }));

    it('ngOnDestroy detiene la cuenta atrás', fakeAsync(() => {
      showSent();
      fixture.destroy();
      // Si el intervalo siguiera vivo, fakeAsync fallaría por timers pendientes
      expect(component.resendCooldown).toBe(0);
    }));
  });
});
