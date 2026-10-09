import { Component, inject, input } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { Offering } from '../../../core/offering.service';
import { RevealDirective } from '../../../shared/reveal.directive';

@Component({
  selector: 'app-final-cta',
  standalone: true,
  imports: [CurrencyPipe, RouterLink, RevealDirective],
  template: `
    <section class="final">
      <div class="container">
        <div class="card" appReveal>
          <div class="card__glow" aria-hidden="true"></div>

          <div class="card__content">
            <span class="eyebrow">
              <span class="eyebrow__dot" aria-hidden="true"></span>
              Empieza hoy
            </span>
            <h2>Tu próxima sesión puede cambiar cómo escribes código</h2>
            <p class="lead">
              Resuelve ese bloqueo de arquitectura, revisa tu proyecto con un experto
              y avanza con un plan claro desde la primera mentoría.
            </p>

            <ul class="trust">
              @for (item of trust; track item) {
                <li>
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
                  {{ item }}
                </li>
              }
            </ul>

            <div class="actions">
              @if (auth.hasRole('CUSTOMER')) {
                <a class="btn btn--primary" routerLink="/book">
                  Reservar una sesión <span class="btn__arrow" aria-hidden="true">→</span>
                </a>
                <a class="btn btn--ghost" routerLink="/bookings/me">Ver mis reservas</a>
              } @else if (!auth.isAuthenticated()) {
                <a class="btn btn--primary" routerLink="/login" [queryParams]="{ mode: 'register' }">
                  Crear cuenta gratis <span class="btn__arrow" aria-hidden="true">→</span>
                </a>
                <a class="btn btn--ghost" routerLink="/servicios">Explorar mentorías</a>
              } @else {
                <a class="btn btn--primary" routerLink="/servicios">
                  Ver el catálogo <span class="btn__arrow" aria-hidden="true">→</span>
                </a>
              }
            </div>
            <p class="note">
              {{ auth.isAuthenticated() ? 'Tu próxima mentoría está a unos clics.' : 'Registro gratuito en menos de un minuto.' }}
            </p>
          </div>

          <!-- Vista previa ilustrativa de cómo se ve una reserva confirmada, usando un servicio real del catálogo -->
          @if (featured(); as offering) {
            <div class="preview" aria-hidden="true">
              <div class="ticket">
                <div class="ticket__head">
                  <span class="ticket__avatar">SB</span>
                  <div>
                    <strong>{{ offering.title }}</strong>
                    <span>Sesión 1:1 · 60 min</span>
                  </div>
                </div>
                <div class="ticket__row">
                  <span>Estado</span>
                  <span class="ticket__status"><i></i> Confirmada</span>
                </div>
                <div class="ticket__row">
                  <span>Inversión</span>
                  <strong>{{ offering.price | currency:'COP':'symbol-narrow':'1.0-0' }}</strong>
                </div>
              </div>
              <div class="bubble bubble--ai">
                <span>✦</span> Recomendado por IA
              </div>
              <div class="bubble bubble--instant">
                <span>⚡</span> Confirmación inmediata
              </div>
            </div>
          }
        </div>
      </div>
    </section>
  `,
  styleUrls: ['./final-cta.component.css']
})
export class FinalCtaComponent {
  readonly auth = inject(AuthService);
  /** Servicio que se usa en la tarjeta de ejemplo; si no hay catálogo, la tarjeta no se muestra. */
  readonly featured = input<Offering | undefined>();

  readonly trust = ['Sesiones 1:1 en vivo', 'Recomendaciones con IA', 'Especialistas verificados'];
}
