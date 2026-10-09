import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Offering, OfferingService } from '../../core/offering.service';
import { RevealDirective } from '../../shared/reveal.directive';
import { SERVICE_CONTENT, categoryKey } from './service-content';

type DetailState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'not-found' }
  | { status: 'ready'; offering: Offering; related: Offering[] };

@Component({
  standalone: true,
  imports: [CurrencyPipe, RouterLink, RevealDirective],
  templateUrl: './service-detail.component.html',
  styleUrls: ['./service-detail.component.css']
})
export class ServiceDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly offerings = inject(OfferingService);
  readonly auth = inject(AuthService);

  readonly includes = [
    'Sesión en vivo 1:1 con un especialista',
    'Trabajo sobre tu código o caso real',
    'Resumen con recomendaciones accionables',
    'Confirmación inmediata de la reserva'
  ];

  readonly steps = [
    { title: 'Reserva', text: 'Elige la fecha y hora que mejor te funcione.' },
    { title: 'Confirmación', text: 'Registramos tu reserva y te avisamos al instante.' },
    { title: 'Sesión', text: 'Trabajamos juntos en tu caso con foco práctico.' }
  ];

  // Al navegar entre servicios relacionados se reutiliza el componente, por eso se escucha paramMap
  readonly state = toSignal(
    this.route.paramMap.pipe(
      map(params => params.get('id') ?? ''),
      switchMap(id => this.offerings.list().pipe(
        map((list): DetailState => {
          const offering = list.find(o => o.id === id && o.active);
          if (!offering) return { status: 'not-found' };
          const related = list.filter(o => o.id !== id && o.active).slice(0, 3);
          return { status: 'ready', offering, related };
        }),
        catchError(() => of<DetailState>({ status: 'error' })),
        startWith<DetailState>({ status: 'loading' })
      ))
    ),
    { initialValue: { status: 'loading' } as DetailState }
  );

  readonly offering = computed(() => {
    const s = this.state();
    return s.status === 'ready' ? s.offering : null;
  });

  readonly related = computed(() => {
    const s = this.state();
    return s.status === 'ready' ? s.related : [];
  });

  readonly category = computed(() => categoryKey(this.offering()?.category ?? ''));
  readonly content = computed(() => SERVICE_CONTENT[this.category()]);

  // El visitante crea su cuenta y vuelve directo al formulario de reserva de este servicio
  readonly bookingUrl = computed(() => `/book?offering=${this.offering()?.id ?? ''}`);

  categoryOf(offering: Offering) { return categoryKey(offering.category); }
  iconOf(offering: Offering) { return SERVICE_CONTENT[categoryKey(offering.category)].icon; }
}
