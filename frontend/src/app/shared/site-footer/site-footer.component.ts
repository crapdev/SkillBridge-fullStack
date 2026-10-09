import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

/** Footer global: se muestra en todas las páginas menos en el login, que tiene el suyo. */
@Component({
  selector: 'app-site-footer',
  standalone: true,
  imports: [RouterLink],
  template: `
    <footer class="footer">
      <div class="container">
        <div class="footer__top">
          <div class="brand-col">
            <a routerLink="/" class="brand" aria-label="SkillBridge AI, ir al inicio">
              <span class="brand__logo" aria-hidden="true">
                <svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="3" /><path d="M8 16V9l4 4 4-4v7" /></svg>
              </span>
              <span class="brand__name">SkillBridge <span class="accent">AI</span></span>
            </a>
            <p class="brand-col__text">
              Mentorías técnicas 1:1 con especialistas en backend, frontend y cloud,
              y recomendaciones con IA basadas en nuestro catálogo.
            </p>
            <a class="mentor-link" routerLink="/" fragment="mentores">
              <span class="mentor-link__dot" aria-hidden="true"></span>
              ¿Eres especialista? Únete como mentor
              <span class="mentor-link__arrow" aria-hidden="true">→</span>
            </a>
          </div>

          <nav class="col" aria-label="Plataforma">
            <h2>Plataforma</h2>
            <a routerLink="/">Inicio</a>
            <a routerLink="/servicios">Servicios</a>
            <a routerLink="/sobre-nosotros">Sobre nosotros</a>
          </nav>

          <nav class="col" aria-label="Mentores">
            <h2>Mentores</h2>
            <a routerLink="/" fragment="mentores">Cómo unirte</a>
            @if (!auth.isAuthenticated()) {
              <a routerLink="/login" [queryParams]="{ type: 'provider' }">Postularme</a>
            }
          </nav>

          <nav class="col" aria-label="Tu cuenta">
            <h2>Tu cuenta</h2>
            @if (auth.hasRole('CUSTOMER')) {
              <a routerLink="/bookings/me">Mis reservas</a>
              <a routerLink="/book">Reservar una sesión</a>
              <a routerLink="/ai">Asistente IA</a>
            } @else if (!auth.isAuthenticated()) {
              <a routerLink="/login">Ingresar</a>
              <a routerLink="/login" [queryParams]="{ mode: 'register' }">Crear cuenta</a>
            } @else {
              <button type="button" class="link-btn" (click)="auth.logout()">Cerrar sesión</button>
            }
          </nav>
        </div>

        <div class="footer__bottom">
          <p>© {{ year }} SkillBridge AI · Proyecto integrador · Riwi</p>
        </div>
      </div>
    </footer>
  `,
  styleUrls: ['./site-footer.component.css']
})
export class SiteFooterComponent {
  readonly auth = inject(AuthService);
  readonly year = new Date().getFullYear();
}
