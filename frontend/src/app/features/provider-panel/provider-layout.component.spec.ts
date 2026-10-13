import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProviderLayoutComponent } from './provider-layout.component';
import { AuthService } from '../../core/auth.service';

/** SCRUM-185 (HU-22): layout del panel del proveedor con el estilo del admin */
describe('ProviderLayoutComponent', () => {
  let fixture: ComponentFixture<ProviderLayoutComponent>;
  let auth: jasmine.SpyObj<AuthService>;
  let el: HTMLElement;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    Object.defineProperty(auth, 'email', { value: signal('laura@skillbridge.com') });

    TestBed.configureTestingModule({
      imports: [ProviderLayoutComponent],
      // La campana pide sus notificaciones por HTTP
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: auth }]
    });

    fixture = TestBed.createComponent(ProviderLayoutComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  it('el menú lateral lleva al dashboard, a las mentorías y al catálogo', () => {
    const links = Array.from(el.querySelectorAll('.side__nav a')).map(a => a.getAttribute('href'));
    expect(links).toEqual(['/provider', '/provider/offerings', '/servicios']);
  });

  it('muestra al usuario de la sesión y la campana de notificaciones', () => {
    expect(el.querySelector('.side__user')?.textContent).toContain('laura@skillbridge.com');
    expect(el.querySelector('app-notification-bell')).not.toBeNull();
  });

  it('el botón de menú abre y cierra el panel lateral en móvil', () => {
    const toggle = el.querySelector('.top__menu') as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();
    expect(el.querySelector('.shell')?.classList).toContain('shell--open');

    (el.querySelector('.scrim') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('.shell')?.classList).not.toContain('shell--open');
  });

  it('cerrar sesión vuelve al login', () => {
    (el.querySelector('.side__footer button') as HTMLButtonElement).click();
    expect(auth.logout).toHaveBeenCalledWith('/login');
  });
});
