import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminLayoutComponent } from './admin-layout.component';
import { AuthService } from '../../../core/auth.service';
import { ToastService } from '../shared/toast.service';

describe('AdminLayoutComponent (HU-23 Task 2)', () => {
  let fixture: ComponentFixture<AdminLayoutComponent>;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockToastService: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    mockAuthService = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    Object.defineProperty(mockAuthService, 'email', { value: signal('admin@skillbridge.com') });

    mockToastService = jasmine.createSpyObj<ToastService>('ToastService', ['dismiss', 'success', 'error']);
    Object.defineProperty(mockToastService, 'toasts', { value: signal([]) });

    TestBed.configureTestingModule({
      imports: [AdminLayoutComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: mockAuthService },
        { provide: ToastService, useValue: mockToastService }
      ]
    });

    fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();
  });

  it('Escenario 6: no incluye la opción "Ir al sitio" en el menú lateral ni en el layout', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const allLinks = Array.from(compiled.querySelectorAll('a'));
    const linkTexts = allLinks.map(a => a.textContent?.trim().toLowerCase());

    const irAlSitioLink = linkTexts.find(t => t?.includes('ir al sitio'));
    expect(irAlSitioLink).toBeUndefined();

    // Confirmar que ningún enlace apunte a la ruta raíz '/'
    const homeLink = allLinks.find(a => a.getAttribute('routerLink') === '/');
    expect(homeLink).toBeUndefined();
  });

  it('Escenario 6: conserva todas las demás opciones de navegación del panel administrativo', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const navLinks = Array.from(compiled.querySelectorAll('.side__nav a'));
    const navTexts = navLinks.map(a => a.textContent?.trim());

    expect(navTexts).toContain('Dashboard');
    expect(navTexts).toContain('Proveedores');
    expect(navTexts).toContain('Clientes');

    const logoutBtn = compiled.querySelector('button.side__link') as HTMLButtonElement;
    expect(logoutBtn).not.toBeNull();
    expect(logoutBtn.textContent?.trim()).toContain('Cerrar sesión');

    logoutBtn.click();
    expect(mockAuthService.logout).toHaveBeenCalledWith('/login');
  });
});
