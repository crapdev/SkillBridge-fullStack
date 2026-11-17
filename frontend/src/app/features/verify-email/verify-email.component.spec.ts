import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent (HU #39)', () => {
  let http: HttpTestingController;
  let navigate: jasmine.Spy;
  const URL = '/api/verification/verify';
  const accepted = { status: 202, statusText: 'Accepted' };

  function render(params: Record<string, string>): ComponentFixture<VerifyEmailComponent> {
    TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(params) } } }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    return fixture;
  }

  const root = (f: ComponentFixture<VerifyEmailComponent>) => f.nativeElement as HTMLElement;
  const text = (f: ComponentFixture<VerifyEmailComponent>) => root(f).textContent ?? '';
  // Respuesta 400 con el motivo por el que el enlace no sirve
  const flushBadLink = (reason: string) =>
    http.expectOne(URL).flush({ status: 400, detail: 'x', reason }, { status: 400, statusText: 'Bad Request' });

  afterEach(() => http.verify());

  it('cliente verificado (200): navega a /ai reemplazando la URL', () => {
    render({ token: 'abc' });
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ token: 'abc' });
    req.flush({ email: 'a@test.com', name: 'Ana', role: 'CUSTOMER' });

    expect(navigate).toHaveBeenCalledWith('/ai', { replaceUrl: true });
  });

  it('proveedor (202): estado provider y muestra el mensaje', () => {
    const fixture = render({ token: 'abc' });
    http.expectOne(URL).flush({ status: 'PENDING_APPROVAL', message: 'Un admin revisará tu perfil' }, accepted);
    fixture.detectChanges();

    expect(fixture.componentInstance.state).toBe('provider');
    expect(text(fixture)).toContain('Un admin revisará tu perfil');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('400 TOKEN_EXPIRED: estado expired', () => {
    const fixture = render({ token: 'abc' });
    flushBadLink('TOKEN_EXPIRED');
    fixture.detectChanges();

    expect(fixture.componentInstance.state).toBe('expired');
    expect(text(fixture)).toContain('El enlace venció');
  });

  it('400 TOKEN_USED: estado used', () => {
    const fixture = render({ token: 'abc' });
    flushBadLink('TOKEN_USED');
    fixture.detectChanges();

    expect(fixture.componentInstance.state).toBe('used');
    expect(text(fixture)).toContain('ya estaba verificado');
  });

  it('400 TOKEN_INVALID: estado invalid', () => {
    const fixture = render({ token: 'abc' });
    flushBadLink('TOKEN_INVALID');
    fixture.detectChanges();

    expect(fixture.componentInstance.state).toBe('invalid');
    expect(text(fixture)).toContain('El enlace no es válido');
  });

  it('sin token en la URL: estado invalid y ninguna petición', () => {
    const fixture = render({});

    http.expectNone(URL);
    expect(fixture.componentInstance.state).toBe('invalid');
  });

  it('error 500: estado failed y Reintentar vuelve a llamar a verify', () => {
    const fixture = render({ token: 'abc' });
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.componentInstance.state).toBe('failed');

    const retry = root(fixture).querySelector('button.submit') as HTMLButtonElement;
    expect(retry.textContent).toContain('Reintentar');
    retry.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.state).toBe('verifying');
    http.expectOne(URL).flush({ email: 'a@test.com', name: 'Ana', role: 'CUSTOMER' });
    expect(navigate).toHaveBeenCalledWith('/ai', { replaceUrl: true });
  });

  it('error de red: estado failed', () => {
    const fixture = render({ token: 'abc' });
    http.expectOne(URL).error(new ProgressEvent('error'));

    expect(fixture.componentInstance.state).toBe('failed');
  });

  describe('formulario de reenvío (enlace vencido)', () => {
    async function expiredAndSubmit(): Promise<ComponentFixture<VerifyEmailComponent>> {
      const fixture = render({ token: 'abc' });
      flushBadLink('TOKEN_EXPIRED');
      fixture.detectChanges();
      await fixture.whenStable();
      const input = root(fixture).querySelector('#email') as HTMLInputElement;
      input.value = 'ana@test.com';
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      fixture.detectChanges();
      (root(fixture).querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      return fixture;
    }

    it('éxito: muestra el aviso neutral y oculta el formulario', async () => {
      const fixture = await expiredAndSubmit();
      const req = http.expectOne('/api/verification/resend');
      expect(req.request.body).toEqual({ email: 'ana@test.com' });
      req.flush({ message: 'ok' }, accepted);
      fixture.detectChanges();

      expect(text(fixture)).toContain('Si ese correo tiene un registro pendiente');
      expect(root(fixture).querySelector('form')).toBeNull();
    });

    it('429: muestra el detail del backend', async () => {
      const fixture = await expiredAndSubmit();
      http.expectOne('/api/verification/resend')
        .flush({ detail: 'Demasiados intentos' }, { status: 429, statusText: 'Too Many Requests' });
      fixture.detectChanges();

      expect(text(fixture)).toContain('Demasiados intentos');
    });
  });
});
