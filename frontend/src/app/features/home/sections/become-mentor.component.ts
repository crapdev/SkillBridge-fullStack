import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { RevealDirective } from '../../../shared/reveal.directive';

@Component({
  selector: 'app-become-mentor',
  standalone: true,
  imports: [RouterLink, RevealDirective],
  template: `
    <section id="mentores" class="mentor">
      <div class="mentor__orb mentor__orb--a" aria-hidden="true"></div>
      <div class="mentor__orb mentor__orb--b" aria-hidden="true"></div>

      <div class="container mentor__grid">
        <div appReveal>
          <span class="eyebrow">Para mentores</span>
          <h2>Comparte lo que sabes y <span class="highlight">haz crecer tu carrera</span></h2>
          <p class="mentor__lead">
            Si dominas backend, frontend o cloud, únete como mentor y ayuda a otros desarrolladores
            a resolver problemas reales mientras construyes tu reputación profesional.
          </p>

          <ul class="benefits">
            @for (benefit of benefits; track benefit; let i = $index) {
              <li appReveal [revealDelay]="150 + i * 80">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
                {{ benefit }}
              </li>
            }
          </ul>
        </div>

        <aside class="join" appReveal [revealDelay]="200" aria-labelledby="join-title">
          <h3 id="join-title">Cómo unirte</h3>
          <ol class="join__steps">
            @for (step of joinSteps; track step.title; let i = $index) {
              <li>
                <span class="join__number">{{ i + 1 }}</span>
                <div>
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.text }}</span>
                </div>
              </li>
            }
          </ol>

          @if (!auth.isAuthenticated()) {
            <a class="join__cta" routerLink="/login" [queryParams]="{ type: 'provider' }">
              Postularme como mentor <span class="join__arrow" aria-hidden="true">→</span>
            </a>
            <p class="join__note">Toma menos de 2 minutos. Sin costo.</p>
          } @else if (auth.hasRole('PROVIDER')) {
            <p class="join__note join__note--ok">Ya formas parte de nuestro equipo de mentores.</p>
          } @else {
            <p class="join__note">Para postularte, cierra sesión y crea una cuenta de mentor.</p>
          }
        </aside>
      </div>
    </section>
  `,
  styleUrls: ['./become-mentor.component.css']
})
export class BecomeMentorComponent {
  readonly auth = inject(AuthService);

  readonly benefits = [
    'Publica tus servicios y define el valor de cada sesión',
    'Organiza tu disponibilidad por horarios',
    'Recibe reservas de personas interesadas en tu especialidad',
    'Perfil revisado por nuestro equipo para garantizar calidad'
  ];

  readonly joinSteps = [
    { title: 'Envía tu solicitud', text: 'Crea tu cuenta eligiendo "Quiero enseñar".' },
    { title: 'Revisamos tu perfil', text: 'Un administrador valida y aprueba tu cuenta.' },
    { title: 'Publica tus mentorías', text: 'Empieza a recibir reservas de la comunidad.' }
  ];
}
