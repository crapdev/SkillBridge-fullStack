import { Component, HostListener, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';
import { AlertHostComponent } from './shared/alert-host.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AlertHostComponent],
  template: `
    <!-- La pantalla de login tiene su propio encabezado -->
    @if (!isAuthPage()) {
    <header class="nav">
      <div class="container nav-inner">
        <a routerLink="/" class="brand" aria-label="SkillBridge AI, ir al inicio">
          <span class="brand-logo" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="4" y="4" width="16" height="16" rx="3" />
              <path d="M8 16V9l4 4 4-4v7" />
            </svg>
          </span>
          <span class="brand-text">
            <span class="brand-name">SkillBridge <span class="brand-accent">AI</span></span>
            <span class="brand-tagline">Engineering Hub</span>
          </span>
        </a>
        <button type="button" class="menu-toggle" (click)="menuOpen.set(!menuOpen())"
                [attr.aria-expanded]="menuOpen()" aria-controls="nav-menu"
                [attr.aria-label]="menuOpen() ? 'Cerrar menú' : 'Abrir menú'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
            @if (menuOpen()) {
              <path d="M6 6l12 12M18 6L6 18" />
            } @else {
              <path d="M4 7h16M4 12h16M4 17h16" />
            }
          </svg>
        </button>
        <!-- En escritorio este contenedor no existe visualmente (display: contents); en móvil es el panel desplegable -->
        <div id="nav-menu" class="nav-menu" [class.open]="menuOpen()" (click)="closeOnSelect($event)">
          <nav class="nav-links">
            <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Inicio</a>
            <a routerLink="/servicios" routerLinkActive="active">Servicios</a>
            <!-- Solo para clientes con sesión; el backend también restringe estas rutas al rol CUSTOMER -->
            @if (auth.hasRole('CUSTOMER')) {
              <a routerLink="/book" routerLinkActive="active">Reservar</a>
              <a routerLink="/ai" routerLinkActive="active">IA &amp; Cloud <span class="badge">Nuevo</span></a>
            } @else if (!auth.isAuthenticated()) {
              <!-- Cómo funciona y Contacto apuntan a secciones del inicio; funcionan en cuanto existan esos id -->
              <a routerLink="/" fragment="como-funciona">Cómo funciona</a>
              <a routerLink="/sobre-nosotros" routerLinkActive="active">Sobre nosotros</a>
              <a routerLink="/" fragment="contacto">Contacto</a>
            }
          </nav>
          <div class="nav-actions">
            @if (!auth.isAuthenticated()) {
              <a routerLink="/login" class="nav-login">Ingresar</a>
            } @else {
              <button type="button" class="nav-login" (click)="auth.logout()">Salir</button>
            }
            @if (auth.hasRole('CUSTOMER')) {
              <a routerLink="/bookings/me" class="button-primary">Mis Reservas</a>
            } @else if (!auth.isAuthenticated()) {
              <a routerLink="/login" [queryParams]="{ mode: 'register' }" class="button-primary">Crear cuenta</a>
            }
          </div>
        </div>
      </div>
    </header>
    }
    <main><router-outlet /></main>
    <app-alert-host />
  `,
  styleUrls: ['./header.css']
})
export class AppComponent {
  auth = inject(AuthService);
  private router = inject(Router);

  readonly menuOpen = signal(false);

  readonly isAuthPage = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(e => e.urlAfterRedirects.startsWith('/login'))
    ),
    { initialValue: false }
  );

  // Se cierra al elegir una opción; "Salir" hacia la misma URL no dispara navegación, por eso no basta con escuchar el router
  closeOnSelect(event: Event): void {
    if ((event.target as Element).closest('a, button')) this.menuOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  closeMenu(): void { this.menuOpen.set(false); }
}
