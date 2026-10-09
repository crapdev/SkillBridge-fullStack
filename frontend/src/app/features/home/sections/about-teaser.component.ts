import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RevealDirective } from '../../../shared/reveal.directive';

@Component({
  selector: 'app-about-teaser',
  standalone: true,
  imports: [RouterLink, RevealDirective],
  template: `
    <section class="about">
      <div class="container about__grid">
        <div class="about__text" appReveal>
          <span class="eyebrow">Sobre nosotros</span>
          <h2>Tecnología real para un aprendizaje real</h2>
          <p>
            SkillBridge AI nació como proyecto integrador de arquitectura y computación en la nube.
            Conectamos a quienes quieren crecer con especialistas que trabajan sobre casos reales,
            usando las mismas prácticas de los equipos profesionales.
          </p>
          <a routerLink="/sobre-nosotros" class="about__link">
            Conoce nuestra historia <span aria-hidden="true">→</span>
          </a>
        </div>

        <ul class="about__features">
          @for (feature of features; track feature.title; let i = $index) {
            <li class="feature" appReveal [revealDelay]="120 + i * 100">
              <span class="feature__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24"><path [attr.d]="feature.icon" /></svg>
              </span>
              <div>
                <h3>{{ feature.title }}</h3>
                <p>{{ feature.text }}</p>
              </div>
            </li>
          }
        </ul>
      </div>
    </section>
  `,
  styleUrls: ['./about-teaser.component.css']
})
export class AboutTeaserComponent {
  readonly features = [
    {
      icon: 'M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5',
      title: 'Arquitectura sólida',
      text: 'Construida con arquitectura hexagonal, pruebas automáticas y CI en cada cambio.'
    },
    {
      icon: 'M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6zM9 12l2 2 4-4',
      title: 'Segura por diseño',
      text: 'Autenticación con JWT, permisos por rol y datos protegidos en cada solicitud.'
    },
    {
      icon: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z',
      title: 'IA que no inventa',
      text: 'El asistente recomienda únicamente servicios que existen en nuestro catálogo.'
    }
  ];
}
