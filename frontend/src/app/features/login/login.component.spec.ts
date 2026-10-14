import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { LoginComponent } from './login.component';

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
