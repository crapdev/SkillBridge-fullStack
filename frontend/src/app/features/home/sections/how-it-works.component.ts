import { Component } from '@angular/core';
import { RevealDirective } from '../../../shared/reveal.directive';

@Component({
  selector: 'app-how-it-works',
  standalone: true,
  imports: [RevealDirective],
  template: `
    <section id="como-funciona" class="how">
      <div class="container">
        <header class="head" appReveal>
          <span class="eyebrow">Cómo funciona</span>
          <h2>De la duda a la solución en tres pasos</h2>
          <p>Sin procesos complicados: encuentra al especialista indicado y empieza a avanzar.</p>
        </header>

        <ol class="steps" appReveal>
          @for (step of steps; track step.title; let i = $index) {
            <li class="step" [style.--i]="i">
              <span class="step__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24"><path [attr.d]="step.icon" /></svg>
                <span class="step__number">{{ i + 1 }}</span>
              </span>
              <h3>{{ step.title }}</h3>
              <p>{{ step.text }}</p>
            </li>
          }
        </ol>
      </div>
    </section>
  `,
  styleUrls: ['./how-it-works.component.css']
})
export class HowItWorksComponent {
  readonly steps = [
    {
      icon: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-3.5-3.5',
      title: 'Explora el catálogo',
      text: 'Filtra por tecnología o pide a la IA que te recomiende la mentoría que mejor encaja contigo.'
    },
    {
      icon: 'M4 7h16v13H4zM4 11h16M9 3v4M15 3v4',
      title: 'Reserva tu sesión',
      text: 'Elige fecha y hora. Tu reserva queda registrada al instante y la puedes consultar cuando quieras.'
    },
    {
      icon: 'M4 21a8 8 0 0 1 16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
      title: 'Aprende con un experto',
      text: 'Trabaja tu caso real en una sesión 1:1 y llévate recomendaciones que puedes aplicar de inmediato.'
    }
  ];
}
