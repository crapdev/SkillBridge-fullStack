import { Component, computed, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AvailabilitySlot, Offering, OfferingService } from '../../core/offering.service';
import { RevealDirective } from '../../shared/reveal.directive';
import { formatDays, formatDuration, formatTimeRange, levelLabel } from '../../shared/offering-format';
import { SERVICE_CONTENT, categoryKey } from './service-content';

/** Cuántos horarios próximos se muestran en el detalle; el resto se ve al reservar */
const MAX_UPCOMING_SLOTS = 6;
const slotDayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);

type DetailState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'not-found' }
  | { status: 'ready'; offering: Offering; related: Offering[]; slots: AvailabilitySlot[] };

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
      switchMap(id =>
        forkJoin({
          offering: this.offerings.getById(id),
          relatedList: this.offerings.list().pipe(catchError(() => of([] as Offering[]))),
          // Si los horarios fallan, el detalle se muestra igual (sin la lista de próximos horarios)
          slots: this.offerings.availableSlots(id).pipe(catchError(() => of([] as AvailabilitySlot[])))
        }).pipe(
          map(({ offering, relatedList, slots }): DetailState => {
            const related = relatedList.filter(o => o.id !== id && o.active).slice(0, 3);
            return { status: 'ready', offering, related, slots };
          }),
          catchError((err: any) => {
            if (err?.status === 404) return of<DetailState>({ status: 'not-found' });
            return of<DetailState>({ status: 'error' });
          }),
          startWith<DetailState>({ status: 'loading' })
        )
      )
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

  // ---- Ficha de la mentoría (HU-20): temario real, nivel, duración y días ----
  readonly syllabus = computed(() => this.offering()?.syllabus ?? []);
  readonly durationText = computed(() => formatDuration(this.offering()?.durationMinutes ?? 60));
  readonly levelText = computed(() => levelLabel(this.offering()?.level));
  readonly daysText = computed(() => formatDays(this.offering()?.workingDays ?? []));

  /** Próximos horarios libres con hora de inicio y de finalización */
  readonly upcomingSlots = computed(() => {
    const s = this.state();
    if (s.status !== 'ready') return [];
    const duration = s.offering.durationMinutes ?? 60;
    return [...s.slots]
      .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))
      .slice(0, MAX_UPCOMING_SLOTS)
      .map(slot => ({
        id: slot.id,
        day: capitalize(slotDayFormat.format(new Date(slot.scheduledAt))),
        range: formatTimeRange(slot.scheduledAt, duration)
      }));
  });
  readonly moreSlots = computed(() => {
    const s = this.state();
    return s.status === 'ready' ? Math.max(0, s.slots.length - MAX_UPCOMING_SLOTS) : 0;
  });

  // El visitante crea su cuenta y vuelve directo al formulario de reserva de este servicio
  readonly bookingUrl = computed(() => `/book?offering=${this.offering()?.id ?? ''}`);

  categoryOf(offering: Offering) { return categoryKey(offering.category); }
  iconOf(offering: Offering) { return SERVICE_CONTENT[categoryKey(offering.category)].icon; }
}
